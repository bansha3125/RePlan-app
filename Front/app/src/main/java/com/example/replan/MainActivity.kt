package com.example.replan

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val todoList = mutableListOf<Todo>()
    private lateinit var todoAdapter: TodoAdapter

    private var currentGeneratedSchedules = listOf<GeneratedScheduleDto>()
    private var currentFixedSchedules = listOf<FixedScheduleDto>()
    private var replayChanges = listOf<ScheduleChangeDto>()
    private var currentStepIndex = -1
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private var playRunnable: Runnable? = null

    private val completedStepsMap = mutableMapOf<String, MutableSet<Int>>()

    // ★ [백엔드 가이드 반영] 유저가 미루기로 선택한 Task ID 목록 임시 보관
    private val pendingPostponedTaskIds = mutableSetOf<Long>()

    private var currentWeekCalendar: Calendar = Calendar.getInstance(Locale.KOREA).apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }

    private val MINUTE_FACTOR: Float = 26f / 60f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ApiClient.init(this)

        val today = Calendar.getInstance(Locale.KOREA)
        currentWeekCalendar = Calendar.getInstance(Locale.KOREA).apply {
            firstDayOfWeek = Calendar.MONDAY
            time = today.time
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }

        updateHeaderWeekRangeText()
        setupDayTabs()

        val rvTodoList = findViewSafely<RecyclerView>("rvTodoList")
        if (rvTodoList != null) {
            todoAdapter = TodoAdapter(
                todoList = todoList,
                onItemClick = { todo ->
                    showAiDecompositionBottomSheet(todo)
                },
                onEditClick = { todo ->
                    showEditTodoBottomSheet(todo)
                },
                onDeleteClick = { todo ->
                    confirmDeleteTask(todo)
                }
            )
            rvTodoList.adapter = todoAdapter
            rvTodoList.layoutManager = LinearLayoutManager(this)
        }

        val fabAdd = findViewSafely<View>("fabAdd") ?: findViewSafely<View>("fab_add")
        fabAdd?.setOnClickListener { showAddTodoBottomSheet() }

        val btnAddFixed = findViewSafely<Button>("btnAddFixed")
        btnAddFixed?.setOnClickListener { showFixedScheduleBottomSheet() }

        // ★ [가이드 반영] AI 재배치 파이프라인 버튼 연결
        val btnAutoSort = findViewSafely<Button>("btnAutoSort")
        btnAutoSort?.setOnClickListener { executeAiReplanPipeline() }

        val btnPrevWeek = findViewSafely<View>("btnPrevWeek") ?: findViewSafely<View>("btn_prev")
        val btnNextWeek = findViewSafely<View>("btnNextWeek") ?: findViewSafely<View>("btn_next")

        btnPrevWeek?.setOnClickListener { changeWeek(-1) }
        btnNextWeek?.setOnClickListener { changeWeek(1) }

        setupDragAndDropContainers()
        setupReplayController()
        loadWeeklySchedulesFromServer()
    }

    private fun <T : View> findViewSafely(idName: String): T? {
        val id = resources.getIdentifier(idName, "id", packageName)
        return if (id != 0) findViewById(id) else null
    }

    private fun confirmDeleteTask(todo: Todo) {
        AlertDialog.Builder(this)
            .setTitle("할 일 삭제")
            .setMessage("'${todo.name}' 할 일을 삭제하시겠습니까?\n관련 AI 일정도 함께 제거됩니다.")
            .setPositiveButton("삭제") { _, _ ->
                val taskIdLong = todo.id.toLongOrNull() ?: return@setPositiveButton

                simulateServerLoading("할 일을 삭제 중입니다...") {
                    lifecycleScope.launch {
                        try {
                            val response = ApiClient.service.deleteTask(taskId = taskIdLong)
                            if (response.isSuccessful) {
                                todoList.removeAll { it.id == todo.id }
                                if (::todoAdapter.isInitialized) todoAdapter.notifyDataSetChanged()

                                Toast.makeText(this@MainActivity, "'${todo.name}' 삭제 완료!", Toast.LENGTH_SHORT).show()
                                loadWeeklySchedulesFromServer()
                            } else {
                                Toast.makeText(this@MainActivity, "삭제 실패", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@MainActivity, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditTodoBottomSheet(todo: Todo) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_todo, null)
        bottomSheetDialog.setContentView(view)

        fun <T : View> findInView(idName: String): T? {
            val id = resources.getIdentifier(idName, "id", packageName)
            return if (id != 0) view.findViewById(id) else null
        }

        val etTodoName = findInView<EditText>("etTodoName")
        val etExpectedTime = findInView<EditText>("etExpectedTime")
        val btnRegisterTodo = findInView<Button>("btnRegisterTodo")
        val tvTitle = findInView<TextView>("tvBottomSheetTitle")

        val btnPriorityHigh = findInView<Button>("btnPriorityHigh")
        val btnPriorityMedium = findInView<Button>("btnPriorityMedium")
        val btnPriorityLow = findInView<Button>("btnPriorityLow")

        val btnAiNone = findInView<Button>("btnAiNone")
        val btnAi3Steps = findInView<Button>("btnAi3Steps")
        val btnAi5Steps = findInView<Button>("btnAi5Steps")
        val btnAi7Steps = findInView<Button>("btnAi7Steps")

        tvTitle?.text = "✍️ 할 일 수정하기"
        btnRegisterTodo?.text = "수정 완료"
        etTodoName?.setText(todo.name)
        etExpectedTime?.setText(todo.expectedTime.toString())

        var selectedPriorityInt = when (todo.priority) { "상" -> 3; "하" -> 1; else -> 2 }
        val priorityButtons = listOfNotNull(btnPriorityHigh, btnPriorityMedium, btnPriorityLow)
        val priorityValues = listOf(3, 2, 1)

        fun updatePriorityUi(targetValue: Int) {
            selectedPriorityInt = targetValue
            priorityButtons.forEachIndexed { idx, btn ->
                if (priorityValues[idx] == selectedPriorityInt) {
                    btn.setBackgroundColor(Color.parseColor("#283593"))
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.setBackgroundColor(Color.parseColor("#D1D1D6"))
                    btn.setTextColor(Color.parseColor("#333333"))
                }
            }
        }
        updatePriorityUi(selectedPriorityInt)

        priorityButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                updatePriorityUi(priorityValues[index])
            }
        }

        var desiredSteps = if (todo.desiredSteps > 0) todo.desiredSteps else 3
        val aiButtons = listOfNotNull(btnAiNone, btnAi3Steps, btnAi5Steps, btnAi7Steps)
        val stepValues = listOf(0, 3, 5, 7)

        fun updateAiStepsUi(targetStep: Int) {
            desiredSteps = targetStep
            aiButtons.forEachIndexed { idx, btn ->
                if (stepValues[idx] == desiredSteps) {
                    btn.setBackgroundColor(Color.parseColor("#283593"))
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.setBackgroundColor(Color.parseColor("#D1D1D6"))
                    btn.setTextColor(Color.parseColor("#333333"))
                }
            }
        }
        updateAiStepsUi(desiredSteps)

        aiButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                updateAiStepsUi(stepValues[index])
            }
        }

        btnRegisterTodo?.setOnClickListener {
            val updatedName = etTodoName?.text?.toString()?.trim() ?: ""
            val updatedTimeStr = etExpectedTime?.text?.toString() ?: ""

            if (updatedName.isEmpty()) {
                Toast.makeText(this, "할 일 이름을 입력해 주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val expectedMinutes = if (updatedTimeStr.isEmpty()) 120 else updatedTimeStr.toInt() * 60
            val taskIdLong = todo.id.toLongOrNull() ?: return@setOnClickListener

            val updateRequest = CreateTaskApiRequest(
                title = updatedName,
                deadline = getSelectedWeekSundayDeadline(),
                estimatedMinutes = expectedMinutes,
                priority = selectedPriorityInt,
                useAiDecomposition = (desiredSteps > 0),
                desiredSteps = desiredSteps
            )

            bottomSheetDialog.dismiss()

            simulateServerLoading("수정 사항을 저장 중입니다...") {
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.service.updateTask(taskId = taskIdLong, request = updateRequest)
                        if (response.isSuccessful) {
                            Toast.makeText(this@MainActivity, "'$updatedName' 수정 완료!", Toast.LENGTH_SHORT).show()
                            loadWeeklySchedulesFromServer()
                        } else {
                            Toast.makeText(this@MainActivity, "수정 실패", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity, "수정 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        bottomSheetDialog.show()
    }

    private fun setupDayTabs() {
        val dayKeys = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")

        val tempCal = currentWeekCalendar.clone() as Calendar
        tempCal.firstDayOfWeek = Calendar.MONDAY

        dayKeys.forEachIndexed { index, key ->
            val dayNumber = tempCal.get(Calendar.DAY_OF_MONTH)

            val tabView = findViewSafely<TextView>("tvTab$key")
                ?: findViewSafely<TextView>("btnTab$key")
                ?: findViewSafely<TextView>("tab$key")
                ?: findViewSafely<TextView>("tv$key")

            tabView?.let { tv ->
                tv.text = "${dayNumber}\n(${dayNames[index]})"
                tv.gravity = Gravity.CENTER
                tv.textSize = 13f
                tv.setTypeface(null, Typeface.BOLD)

                when (key) {
                    "Sat" -> tv.setTextColor(Color.parseColor("#1976D2"))
                    "Sun" -> tv.setTextColor(Color.parseColor("#D32F2F"))
                    else -> tv.setTextColor(Color.parseColor("#333333"))
                }

                tv.setOnClickListener {
                    showDayDetailPopup(key, dayNumber.toString(), dayNames[index])
                }
            }
            tempCal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun showDayDetailPopup(dayKey: String, dayNumber: String, dayName: String) {
        val bottomSheetDialog = BottomSheetDialog(this)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(24))
            setBackgroundColor(Color.WHITE)
        }

        val tvTitle = TextView(this).apply {
            text = "📌 ${dayNumber}일 (${dayName}) 세부 일정"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A237E"))
            setPadding(0, 0, 0, dpToPx(16))
        }
        rootLayout.addView(tvTitle)

        val dayFixed = currentFixedSchedules.filter { convertDayToKey(it.repeatDay ?: it.startTime) == dayKey }
        val dayGenerated = currentGeneratedSchedules.filter { convertDayToKey(it.startTime) == dayKey }

        fun formatMinToTimeString(minTotal: Int): String {
            val hour = (minTotal / 60) % 24
            val min = minTotal % 60
            return String.format(Locale.KOREA, "%02d:%02d", hour, min)
        }

        if (dayFixed.isEmpty() && dayGenerated.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "등록된 일정이 없습니다. ☕"
                textSize = 13.5f
                setTextColor(Color.GRAY)
                setPadding(0, dpToPx(12), 0, dpToPx(12))
            }
            rootLayout.addView(tvEmpty)
        } else {
            dayFixed.forEach { fixed ->
                val startMin = parseTimeToMinutes(fixed.startTime)
                val endMin = parseTimeToMinutes(fixed.endTime)
                val timeFormattedStr = "${formatMinToTimeString(startMin)} ~ ${formatMinToTimeString(endMin)}"

                val card = CardView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(52)
                    ).apply { setMargins(0, 0, 0, dpToPx(8)) }
                    radius = dpToPx(8).toFloat()
                    setCardBackgroundColor(Color.parseColor("#F0F0F0"))
                }
                val tv = TextView(this).apply {
                    text = "[고정] ${fixed.title}\n⏰ $timeFormattedStr"
                    setTextColor(Color.parseColor("#333333"))
                    textSize = 12.5f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(14), 0, dpToPx(14), 0)
                }
                card.addView(tv)
                rootLayout.addView(card)
            }

            dayGenerated.forEach { gen ->
                val startMin = parseTimeToMinutes(gen.startTime)
                val endMin = parseTimeToMinutes(gen.endTime)
                val timeFormattedStr = "${formatMinToTimeString(startMin)} ~ ${formatMinToTimeString(endMin)}"

                val card = CardView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(56)
                    ).apply { setMargins(0, 0, 0, dpToPx(8)) }
                    radius = dpToPx(8).toFloat()
                    setCardBackgroundColor(Color.parseColor(if (gen.completed) "#66BB6A" else if (gen.locked) "#1A237E" else "#283593"))
                }
                val tv = TextView(this).apply {
                    text = "${gen.title}\n⏰ $timeFormattedStr"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(14), 0, dpToPx(14), 0)
                }
                card.addView(tv)
                rootLayout.addView(card)
            }
        }

        bottomSheetDialog.setContentView(rootLayout)
        bottomSheetDialog.show()
    }

    private fun renderAllWeeklySchedules() {
        clearAllContainers()
        currentFixedSchedules.forEach { renderFixedScheduleCard(it) }
        currentGeneratedSchedules.forEach { renderGeneratedScheduleCard(it) }
        scrollToMorningEight()
    }

    private fun scrollToMorningEight() {
        val rootView = findViewById<View>(android.R.id.content) ?: return
        var targetScrollView: View? = null

        fun findScrollViewRecursive(view: View) {
            if (view is ScrollView || view is NestedScrollView) {
                targetScrollView = view
                return
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    findScrollViewRecursive(view.getChildAt(i))
                    if (targetScrollView != null) return
                }
            }
        }

        findScrollViewRecursive(rootView)
        val targetY = dpToPx((480 * MINUTE_FACTOR).toInt())

        handler.postDelayed({
            targetScrollView?.let { sv ->
                if (sv is ScrollView) sv.smoothScrollTo(0, targetY)
                else if (sv is NestedScrollView) sv.smoothScrollTo(0, targetY)
            }
        }, 200)
    }

    private fun changeWeek(amount: Int) {
        currentWeekCalendar.add(Calendar.WEEK_OF_YEAR, amount)
        updateHeaderWeekRangeText()
        setupDayTabs()
        loadWeeklySchedulesFromServer()
    }

    private fun updateHeaderWeekRangeText() {
        val tvWeekRange = findViewSafely<TextView>("tvWeekRange")
        val startCal = currentWeekCalendar.clone() as Calendar
        startCal.firstDayOfWeek = Calendar.MONDAY
        startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val endCal = startCal.clone() as Calendar
        endCal.add(Calendar.DAY_OF_WEEK, 6)

        val startSdf = SimpleDateFormat("M월 d일", Locale.KOREA)
        val textStr = "${startSdf.format(startCal.time)} - ${startSdf.format(endCal.time)}"
        tvWeekRange?.text = textStr
    }

    private fun getSelectedWeekStartDate(): String {
        val cal = currentWeekCalendar.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return sdf.format(cal.time)
    }

    private fun getSelectedWeekSundayDeadline(): String {
        val cal = currentWeekCalendar.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.add(Calendar.DAY_OF_WEEK, 6)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'23:59:59", Locale.KOREA)
        return sdf.format(cal.time)
    }

    private fun loadWeeklySchedulesFromServer() {
        simulateServerLoading("일정을 불러오는 중입니다...") {
            lifecycleScope.launch {
                try {
                    val targetWeek = getSelectedWeekStartDate()
                    val currentUserId = 1L

                    val response: WeeklyScheduleResponse = ApiClient.service.getWeeklySchedules(
                        userId = currentUserId,
                        weekStartDate = targetWeek
                    )

                    currentFixedSchedules = response.fixedSchedules ?: emptyList()
                    currentGeneratedSchedules = response.generatedSchedules ?: emptyList()

                    val tasks = ApiClient.service.getTasks()
                    todoList.clear()

                    tasks.forEach { task ->
                        Log.d("STEP_CHECK", "📌 [할 일]: ${task.title} | useAi: ${task.useAiDecomposition} | 백엔드 desiredSteps: ${task.desiredSteps}")
                    }

                    val processedTaskIds = mutableSetOf<String>()

                    if (tasks.isNotEmpty()) {
                        tasks.forEach { task ->
                            val currentTaskIdStr = task.taskId?.toString() ?: UUID.randomUUID().toString()

                            if (processedTaskIds.contains(currentTaskIdStr)) {
                                return@forEach
                            }
                            processedTaskIds.add(currentTaskIdStr)

                            val cleanTaskTitle = task.title.trim().replace(" ", "").lowercase(Locale.KOREA)

                            val matchedBlocks = currentGeneratedSchedules.filter { gen ->
                                val isTaskIdMatch = task.taskId != null && gen.taskId == task.taskId
                                val cleanGenTitle = gen.title.replace(Regex("\\[.*?\\]"), "").trim().replace(" ", "").lowercase(Locale.KOREA)
                                val isTitleMatch = cleanTaskTitle.isNotEmpty() && (
                                        cleanGenTitle.contains(cleanTaskTitle) || cleanTaskTitle.contains(cleanGenTitle)
                                        )
                                isTaskIdMatch || isTitleMatch
                            }

                            val priorityText = when (task.priority) {
                                3 -> "상"; 1 -> "하"; else -> "중"
                            }

                            // ★ [완벽 해결 로직] 서버의 desiredSteps(3, 5, 7)를 있는 그대로 바인딩
                            val rawSteps = task.desiredSteps ?: 3

                            val steps = if (task.useAiDecomposition == false || rawSteps == 0) {
                                0 // 쪼개기 안 함
                            } else {
                                rawSteps // 서버에서 온 3, 5, 7단계 수치 100% 반영
                            }

                            val isAllDone = matchedBlocks.isNotEmpty() && matchedBlocks.all { it.completed }

                            todoList.add(
                                Todo(
                                    id = currentTaskIdStr,
                                    name = task.title,
                                    deadlineType = task.deadlineType ?: "DATE",
                                    specificScheduleName = null,
                                    expectedTime = (task.estimatedMinutes / 60).coerceAtLeast(1),
                                    priority = priorityText,
                                    isCompleted = isAllDone,
                                    desiredSteps = steps, // 어댑터로 정확히 0, 3, 5, 7 수치 전송
                                    subSteps = emptyList()
                                )
                            )
                        }
                    }

                    if (::todoAdapter.isInitialized) {
                        todoAdapter.notifyDataSetChanged()
                    } else {
                        val rvTodoList = findViewById<RecyclerView>(R.id.rvTodoList)
                        todoAdapter = TodoAdapter(
                            todoList = todoList,
                            onItemClick = { showAiDecompositionBottomSheet(it) },
                            onEditClick = { showEditTodoBottomSheet(it) },
                            onDeleteClick = { confirmDeleteTask(it) }
                        )
                        rvTodoList?.adapter = todoAdapter
                    }

                    renderAllWeeklySchedules()
                    Toast.makeText(this@MainActivity, "일정을 불러왔습니다! ✨", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "일정 조회 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ★ [백엔드 가이드 반영] 임시로 모아둔 미루기/완료 태스크 ID를 묶어 POST /schedules/replan 전달
    private fun executeAiReplanPipeline() {
        val btnAutoSort = findViewSafely<Button>("btnAutoSort")
        btnAutoSort?.isEnabled = false

        val tvAiFeedback = findViewSafely<TextView>("tvAiFeedback")
        tvAiFeedback?.text = "🤖 AI가 일정을 재배치하고 있습니다..."

        val nowIsoTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA).format(Calendar.getInstance().time)
        val completedIds = todoList.filter { it.isCompleted }.mapNotNull { it.id.toLongOrNull() }
        val postponedIds = pendingPostponedTaskIds.toList()

        val replanRequest = ReplanApiRequest(
            userId = 1L,
            replanFromTime = nowIsoTime,
            completedTaskIds = completedIds,
            postponedTaskIds = postponedIds
        )

        simulateServerLoading("AI 일정을 재배치 중입니다... 🤖") {
            lifecycleScope.launch {
                try {
                    Log.d("REPLAN_DEBUG", "🚀 [POST /schedules/replan] Completed: $completedIds, Postponed: $postponedIds")

                    val response = ApiClient.service.replanSchedules(request = replanRequest)

                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "일정이 지능적으로 재배치되었습니다! ✨", Toast.LENGTH_SHORT).show()

                        // 전송 성공 시 임시 미루기 데이터 초기화
                        pendingPostponedTaskIds.clear()

                        // 백엔드가 AI 결과를 DB에 덮어씌웠으므로 주간 스케줄 다시 조회
                        loadWeeklySchedulesFromServer()
                    } else {
                        Toast.makeText(this@MainActivity, "재배치 실패 (${response.code()})", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "재배치 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    btnAutoSort?.isEnabled = true
                }
            }
        }
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val clean = if (timeStr.contains("T")) timeStr.split("T")[1] else timeStr
            val parts = clean.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun renderFixedScheduleCard(fixed: FixedScheduleDto) {
        val dayKey = convertDayToKey(fixed.repeatDay ?: fixed.startTime)
        val container = findViewSafely<LinearLayout>("container$dayKey") ?: return

        val startMin = parseTimeToMinutes(fixed.startTime)
        val endMin = parseTimeToMinutes(fixed.endTime)
        val durationMinutes = (endMin - startMin).coerceAtLeast(30)

        val cardHeightDp = (durationMinutes * MINUTE_FACTOR).toInt().coerceAtLeast(24)

        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(cardHeightDp)
            )
            radius = dpToPx(6).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#E0E0E0"))

            tag = ScheduleCardTag(
                fixedScheduleId = fixed.fixedScheduleId,
                locked = fixed.locked,
                type = "FIXED",
                startMin = startMin,
                endMin = endMin
            )
        }

        val tv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            text = "[고정]\n${fixed.title}"
            setTextColor(Color.parseColor("#424242"))
            textSize = 9.5f
            gravity = Gravity.CENTER
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }
        card.addView(tv)
        enableDragAndDrop(card)

        container.addView(card)
        recalculateContainerLayout(container)
    }

    private fun renderGeneratedScheduleCard(gen: GeneratedScheduleDto) {
        val dayKey = convertDayToKey(gen.startTime)
        val container = findViewSafely<LinearLayout>("container$dayKey") ?: return

        val startMin = parseTimeToMinutes(gen.startTime)
        val endMin = parseTimeToMinutes(gen.endTime)
        val durationMinutes = (endMin - startMin).coerceAtLeast(30)

        val cardHeightDp = (durationMinutes * MINUTE_FACTOR).toInt().coerceAtLeast(24)
        val cardBgColor = when {
            gen.completed -> "#66BB6A"
            gen.locked -> "#1A237E"
            else -> "#283593"
        }

        val totalBlocks = currentGeneratedSchedules.count { it.taskId == gen.taskId }
        val displayTitle = when {
            gen.title.startsWith("[") -> gen.title
            gen.stepOrder <= 1 && totalBlocks <= 1 -> "[${gen.title}] ${gen.title}"
            else -> "[${gen.stepOrder}/$totalBlocks]\n${gen.title}"
        }

        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(cardHeightDp)
            )
            radius = dpToPx(6).toFloat()
            cardElevation = 2f
            setCardBackgroundColor(Color.parseColor(cardBgColor))

            tag = ScheduleCardTag(
                taskId = gen.taskId,
                blockId = gen.blockId,
                type = "AI",
                locked = gen.locked,
                startMin = startMin,
                endMin = endMin
            )
        }

        val tv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            text = displayTitle
            setTextColor(Color.WHITE)
            textSize = 9.5f
            gravity = Gravity.CENTER
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }
        card.addView(tv)
        card.setOnClickListener { showScheduleActionDialog(gen) }
        enableDragAndDrop(card)

        container.addView(card)
        recalculateContainerLayout(container)
    }

    // ★ [백엔드 가이드 반영] 클릭 시 즉시 전송이 아닌 임시 목록에 스태킹
    private fun showScheduleActionDialog(gen: GeneratedScheduleDto) {
        val taskIdLong = gen.taskId
        val isAlreadyPostponed = taskIdLong != null && pendingPostponedTaskIds.contains(taskIdLong)

        val options = arrayOf(
            if (gen.completed) "완료 취소" else "✔ 완료 처리",
            if (isAlreadyPostponed) "⏩ 미루기 선택 해제" else "⏩ 이 일정 미루기"
        )

        AlertDialog.Builder(this)
            .setTitle(gen.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestUpdateScheduleStatus(gen, completed = !gen.completed)
                    1 -> {
                        if (taskIdLong != null) {
                            if (isAlreadyPostponed) {
                                pendingPostponedTaskIds.remove(taskIdLong)
                                Toast.makeText(this, "'${gen.title}' 미루기 선택 해제", Toast.LENGTH_SHORT).show()
                            } else {
                                pendingPostponedTaskIds.add(taskIdLong)
                                Toast.makeText(this, "'${gen.title}' 미루기 목록에 추가됨 ⏩\n'AI 재배치' 버튼을 누르면 일정이 반영됩니다.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "유효하지 않은 일정 ID입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun requestUpdateScheduleStatus(
        gen: GeneratedScheduleDto,
        locked: Boolean? = null,
        completed: Boolean? = null
    ) {
        val updatedGen = gen.copy(
            locked = locked ?: gen.locked,
            completed = completed ?: gen.completed
        )

        val updatedList = currentGeneratedSchedules.toMutableList()
        val index = updatedList.indexOfFirst { it.blockId == gen.blockId }
        if (index != -1) {
            updatedList[index] = updatedGen
            currentGeneratedSchedules = updatedList
        }

        val cleanGenTitle = updatedGen.title.replace(Regex("\\[.*?\\]"), "").trim().replace(" ", "").lowercase(Locale.KOREA)

        val matchedTodo = todoList.find { todo ->
            val isIdMatch = updatedGen.taskId != null && todo.id == updatedGen.taskId.toString()
            val cleanTodoName = todo.name.trim().replace(" ", "").lowercase(Locale.KOREA)
            val isExactTitleMatch = cleanGenTitle.isNotEmpty() && (
                    cleanTodoName.contains(cleanGenTitle) || cleanGenTitle.contains(cleanTodoName)
                    )
            isIdMatch || isExactTitleMatch
        }

        if (matchedTodo != null) {
            val targetTaskIdLong = matchedTodo.id.toLongOrNull()
            val cleanTodoName = matchedTodo.name.trim().replace(" ", "").lowercase(Locale.KOREA)

            val relatedBlocks = currentGeneratedSchedules.filter { b ->
                val isTaskIdMatch = targetTaskIdLong != null && b.taskId == targetTaskIdLong
                val bTitleClean = b.title.trim().replace(" ", "").lowercase(Locale.KOREA)
                val isTitleMatch = cleanTodoName.isNotEmpty() && (
                        bTitleClean.contains(cleanTodoName) || cleanTodoName.contains(bTitleClean)
                        )
                isTaskIdMatch || isTitleMatch
            }.sortedBy { it.stepOrder }

            val blockIdx = relatedBlocks.indexOfFirst { it.blockId == updatedGen.blockId }
            val completedSet = completedStepsMap.getOrPut(matchedTodo.id) { mutableSetOf() }

            if (blockIdx != -1) {
                if (completed == true) {
                    completedSet.add(blockIdx)
                } else if (completed == false) {
                    completedSet.remove(blockIdx)
                }
            }

            val isAllBlocksCompleted = relatedBlocks.isNotEmpty() && relatedBlocks.all { it.completed }

            if (isAllBlocksCompleted) {
                updateTodoCardCompletion(matchedTodo, isAllCompleted = true)
            } else {
                updateTodoCardUncompletion(matchedTodo)
            }
        }

        renderAllWeeklySchedules()

        val blockId = updatedGen.blockId
        if (!blockId.isNullOrEmpty()) {
            lifecycleScope.launch {
                try {
                    val updateBody = UpdateScheduleStatusApiRequest(
                        taskId = updatedGen.taskId,
                        blockId = blockId,
                        locked = updatedGen.locked,
                        completed = completed,
                        startTime = updatedGen.startTime,
                        endTime = updatedGen.endTime
                    )

                    ApiClient.service.updateGeneratedScheduleStatus(
                        blockId = blockId,
                        request = updateBody
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun showAddTodoBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_todo, null)
        bottomSheetDialog.setContentView(view)

        fun <T : View> findInView(idName: String): T? {
            val id = resources.getIdentifier(idName, "id", packageName)
            return if (id != 0) view.findViewById(id) else null
        }

        val etTodoName = findInView<EditText>("etTodoName")
        val etExpectedTime = findInView<EditText>("etExpectedTime")
        val btnRegisterTodo = findInView<Button>("btnRegisterTodo")
        val btnPriorityHigh = findInView<Button>("btnPriorityHigh")
        val btnPriorityMedium = findInView<Button>("btnPriorityMedium")
        val btnPriorityLow = findInView<Button>("btnPriorityLow")

        val btnAiNone = findInView<Button>("btnAiNone")
        val btnAi3Steps = findInView<Button>("btnAi3Steps")
        val btnAi5Steps = findInView<Button>("btnAi5Steps")
        val btnAi7Steps = findInView<Button>("btnAi7Steps")

        var selectedPriorityInt = 2
        val priorityButtons = listOfNotNull(btnPriorityHigh, btnPriorityMedium, btnPriorityLow)
        val priorityValues = listOf(3, 2, 1)

        priorityButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                selectedPriorityInt = priorityValues[index]
                priorityButtons.forEach { targetBtn ->
                    if (targetBtn == btn) {
                        targetBtn.setBackgroundColor(Color.parseColor("#283593"))
                        targetBtn.setTextColor(Color.WHITE)
                    } else {
                        targetBtn.setBackgroundColor(Color.parseColor("#D1D1D6"))
                        targetBtn.setTextColor(Color.parseColor("#333333"))
                    }
                }
            }
        }

        var desiredSteps = 3
        val aiButtons = listOfNotNull(btnAiNone, btnAi3Steps, btnAi5Steps, btnAi7Steps)
        val stepValues = listOf(0, 3, 5, 7)

        aiButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                desiredSteps = stepValues[index]
                aiButtons.forEach { targetBtn ->
                    if (targetBtn == btn) {
                        targetBtn.setBackgroundColor(Color.parseColor("#283593"))
                        targetBtn.setTextColor(Color.WHITE)
                    } else {
                        targetBtn.setBackgroundColor(Color.parseColor("#D1D1D6"))
                        targetBtn.setTextColor(Color.parseColor("#333333"))
                    }
                }
            }
        }

        btnRegisterTodo?.setOnClickListener {
            val todoName = etTodoName?.text?.toString()?.trim() ?: ""
            val expectedTimeStr = etExpectedTime?.text?.toString()?.trim() ?: ""

            if (todoName.isEmpty()) {
                Toast.makeText(this, "할 일 이름을 입력해 주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 소요 시간 예외 처리 (기본 2시간 = 120분)
            val expectedMinutes = try {
                if (expectedTimeStr.isEmpty()) 120 else expectedTimeStr.toInt() * 60
            } catch (e: Exception) {
                120
            }

            // 백엔드 파싱 에러 방지용 마감일 안전 추출 (YYYY-MM-DD)
            val safeDeadlineDate = try {
                getSelectedWeekSundayDeadline()
            } catch (e: Exception) {
                getSelectedWeekStartDate()
            }

            val apiRequest = CreateTaskApiRequest(
                title = todoName,
                deadline = safeDeadlineDate,
                estimatedMinutes = expectedMinutes,
                priority = selectedPriorityInt,
                useAiDecomposition = (desiredSteps > 0),
                desiredSteps = desiredSteps,
                deadlineType = "DATE",
                linkedScheduleId = null
            )

            Log.d("SEND_CHECK", "📤 백엔드 등록 시도 [JSON 데이터]: $apiRequest")

            bottomSheetDialog.dismiss()

            simulateServerLoading("할 일을 등록하는 중입니다...") {
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.service.createTask(request = apiRequest)
                        if (response.isSuccessful) {
                            Log.d("SEND_CHECK", "✅ 백엔드 저장 성공: ${response.body()}")
                            Toast.makeText(this@MainActivity, "'${apiRequest.title}' 등록 완료!", Toast.LENGTH_SHORT).show()
                            loadWeeklySchedulesFromServer()
                        } else {
                            // 500 등 에러 발생 시 백엔드가 던진 상세 메세지 출력
                            val errorBodyStr = response.errorBody()?.string() ?: "알 수 없는 백엔드 에러"
                            Log.e("SEND_CHECK", "❌ 저장 실패 코드: ${response.code()} | 백엔드 에러 내용: $errorBodyStr")
                            Toast.makeText(this@MainActivity, "저장 실패 (코드 ${response.code()}) - 백엔드 로그 확인", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Log.e("SEND_CHECK", "💥 네트워크 통신 예외 발생: ${e.message}")
                        Toast.makeText(this@MainActivity, "통신 에러: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        bottomSheetDialog.show()
    }

    private fun showFixedScheduleBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_fixed_schedule, null)
        bottomSheetDialog.setContentView(view)

        fun <T : View> findInView(idName: String): T? {
            val id = resources.getIdentifier(idName, "id", packageName)
            return if (id != 0) view.findViewById(id) else null
        }

        val etScheduleName = findInView<EditText>("etScheduleName")
        val etStartTime = findInView<EditText>("etStartTime")
        val etEndTime = findInView<EditText>("etEndTime")
        val btnRegister = findInView<Button>("btnRegister")

        val dayButtons = mutableListOf<Pair<Button, String>>()
        val dayConfigs = listOf(
            listOf("btnDayMon", "btnMon", "btnDay1", "btnMonDay") to "MONDAY",
            listOf("btnDayTue", "btnTue", "btnDay2", "btnTueDay") to "TUESDAY",
            listOf("btnDayWed", "btnWed", "btnDay3", "btnWedDay") to "WEDNESDAY",
            listOf("btnDayThu", "btnThu", "btnDay4", "btnThuDay") to "THURSDAY",
            listOf("btnDayFri", "btnFri", "btnDay5", "btnFriDay") to "FRIDAY",
            listOf("btnDaySat", "btnSat", "btnDay6", "btnSatDay") to "SATURDAY",
            listOf("btnDaySun", "btnSun", "btnDay7", "btnSunDay") to "SUNDAY"
        )

        dayConfigs.forEach { (idNames, dayCode) ->
            var matchedBtn: Button? = null
            for (idName in idNames) {
                matchedBtn = findInView<Button>(idName)
                if (matchedBtn != null) break
            }
            if (matchedBtn != null) {
                dayButtons.add(matchedBtn to dayCode)
            }
        }

        val selectedDays = mutableSetOf<String>()

        dayButtons.forEach { (btn: Button, dayCode: String) ->
            btn.setBackgroundColor(Color.parseColor("#D1D1D6"))
            btn.setTextColor(Color.parseColor("#333333"))

            btn.setOnClickListener {
                if (selectedDays.contains(dayCode)) {
                    selectedDays.remove(dayCode)
                    btn.setBackgroundColor(Color.parseColor("#D1D1D6"))
                    btn.setTextColor(Color.parseColor("#333333"))
                } else {
                    selectedDays.add(dayCode)
                    btn.setBackgroundColor(Color.parseColor("#283593"))
                    btn.setTextColor(Color.WHITE)
                }
            }
        }

        fun formatTimeString(rawTime: String, defaultTime: String): String {
            val trimmed = rawTime.trim()
            if (trimmed.isEmpty()) return "$defaultTime:00"
            val parts = trimmed.split(":")
            return when (parts.size) {
                1 -> "${parts[0].padStart(2, '0')}:00:00"
                2 -> "${parts[0].padStart(2, '0')}:${parts[1].padStart(2, '0')}:00"
                else -> if (trimmed.length == 8) trimmed else "$defaultTime:00"
            }
        }

        btnRegister?.setOnClickListener {
            val name = etScheduleName?.text?.toString()?.trim() ?: ""
            val rawStartTime = etStartTime?.text?.toString() ?: ""
            val rawEndTime = etEndTime?.text?.toString() ?: ""

            if (name.isEmpty() || selectedDays.isEmpty()) {
                Toast.makeText(this, "이름과 요일을 입력해 주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val formattedStartTime = formatTimeString(rawStartTime, "15:00")
            val formattedEndTime = formatTimeString(rawEndTime, "18:00")

            bottomSheetDialog.dismiss()

            simulateServerLoading("고정 일정을 저장하는 중입니다...") {
                lifecycleScope.launch {
                    try {
                        var successCount = 0
                        selectedDays.forEach { dayCode ->
                            val targetDayIso = convertDayToIsoDate(dayCode, formattedStartTime)
                            val targetEndDayIso = convertDayToIsoDate(dayCode, formattedEndTime)

                            val apiRequest = CreateFixedScheduleApiRequest(
                                title = name,
                                startTime = targetDayIso,
                                endTime = targetEndDayIso,
                                repeat = false,
                                repeatDay = dayCode
                            )

                            val response = ApiClient.service.createFixedSchedule(request = apiRequest)
                            if (response.isSuccessful) successCount++
                        }

                        if (successCount > 0) {
                            Toast.makeText(this@MainActivity, "'$name' 고정 일정 등록 완료!", Toast.LENGTH_SHORT).show()
                            loadWeeklySchedulesFromServer()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        bottomSheetDialog.show()
    }

    private fun setupReplayController() {
        val btnPrev = findViewSafely<Button>("btnReplayPrev")
        val btnPlay = findViewSafely<Button>("btnReplayPlay")
        val btnNext = findViewSafely<Button>("btnReplayNext")

        btnNext?.setOnClickListener {
            if (currentGeneratedSchedules.isEmpty()) {
                Toast.makeText(this, "배치할 AI 일정이 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentStepIndex < currentGeneratedSchedules.size - 1) {
                currentStepIndex++
                applyReplayStepByDto(currentGeneratedSchedules[currentStepIndex], isForward = true)
            } else {
                Toast.makeText(this, "마지막 단계입니다.", Toast.LENGTH_SHORT).show()
            }
        }

        btnPrev?.setOnClickListener {
            if (currentGeneratedSchedules.isEmpty()) return@setOnClickListener
            if (currentStepIndex >= 0) {
                val dtoToRemove = currentGeneratedSchedules[currentStepIndex]
                applyReplayStepByDto(dtoToRemove, isForward = false)
                currentStepIndex--
            } else {
                Toast.makeText(this, "첫 번째 단계입니다.", Toast.LENGTH_SHORT).show()
            }
        }

        btnPlay?.setOnClickListener {
            if (currentGeneratedSchedules.isEmpty()) {
                Toast.makeText(this, "배치할 AI 일정이 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isPlaying) {
                stopAutoPlay()
                btnPlay.text = "▶ 재생"
            } else {
                if (currentStepIndex >= currentGeneratedSchedules.size - 1) {
                    resetAndStartReplay()
                } else {
                    startAutoPlay()
                }
                btnPlay.text = "⏸ 정지"
            }
        }
    }

    private fun resetAndStartReplay() {
        stopAutoPlay()
        currentStepIndex = -1
        clearGeneratedScheduleCards()
        startAutoPlay()
    }

    private fun startAutoPlay() {
        isPlaying = true
        val btnPlay = findViewSafely<Button>("btnReplayPlay")
        btnPlay?.text = "⏸ 정지"

        playRunnable = object : Runnable {
            override fun run() {
                if (currentStepIndex < currentGeneratedSchedules.size - 1) {
                    currentStepIndex++
                    applyReplayStepByDto(currentGeneratedSchedules[currentStepIndex], isForward = true)
                    handler.postDelayed(this, 1200)
                } else {
                    stopAutoPlay()
                    btnPlay?.text = "▶ 재생"
                    Toast.makeText(this@MainActivity, "AI 일정 배치 완료! ✨", Toast.LENGTH_SHORT).show()
                }
            }
        }
        handler.post(playRunnable!!)
    }

    private fun stopAutoPlay() {
        isPlaying = false
        playRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun clearGeneratedScheduleCards() {
        val dayKeys = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        dayKeys.forEach { key ->
            val container = findViewSafely<LinearLayout>("container$key")
            container?.let {
                val viewsToRemove = mutableListOf<View>()
                for (i in 0 until it.childCount) {
                    val child = it.getChildAt(i)
                    val tagData = child.tag as? ScheduleCardTag
                    if (tagData?.type == "AI") {
                        viewsToRemove.add(child)
                    }
                }
                viewsToRemove.forEach { v -> it.removeView(v) }
                recalculateContainerLayout(it)
            }
        }
    }

    private fun applyReplayStepByDto(gen: GeneratedScheduleDto, isForward: Boolean) {
        val tvAiFeedback = findViewSafely<TextView>("tvAiFeedback")
        val dayKey = convertDayToKey(gen.startTime)
        val targetContainer = findViewSafely<LinearLayout>("container$dayKey") ?: return

        val totalBlocks = currentGeneratedSchedules.size
        val displayTitle = gen.title

        if (isForward) {
            val stepSeq = currentStepIndex + 1
            tvAiFeedback?.text = "[STEP $stepSeq/$totalBlocks] $displayTitle\n💡 AI가 최적의 시간대로 배치했습니다."

            var isAlreadyExist = false
            for (i in 0 until targetContainer.childCount) {
                val card = targetContainer.getChildAt(i) as? CardView
                val tagData = card?.tag as? ScheduleCardTag
                if (tagData != null && (
                            (!tagData.blockId.isNullOrEmpty() && tagData.blockId == gen.blockId) ||
                                    (tagData.taskId != null && gen.taskId != null && tagData.taskId == gen.taskId && tagData.startMin == parseTimeToMinutes(gen.startTime))
                            )) {
                    isAlreadyExist = true
                    break
                }
            }

            if (!isAlreadyExist) {
                renderGeneratedScheduleCard(gen)
            }

        } else {
            if (currentStepIndex > 0) {
                val prevStep = currentGeneratedSchedules[currentStepIndex - 1]
                tvAiFeedback?.text = "[STEP $currentStepIndex/$totalBlocks] ${prevStep.title}"
            } else {
                tvAiFeedback?.text = "🤖 배치 시작 전 단계입니다."
            }

            var targetViewToRemove: View? = null
            for (i in 0 until targetContainer.childCount) {
                val child = targetContainer.getChildAt(i)
                val tagData = child.tag as? ScheduleCardTag
                if (tagData != null && (
                            (!tagData.blockId.isNullOrEmpty() && tagData.blockId == gen.blockId) ||
                                    (tagData.taskId != null && gen.taskId != null && tagData.taskId == gen.taskId && tagData.startMin == parseTimeToMinutes(gen.startTime))
                            )) {
                    targetViewToRemove = child
                    break
                }
            }

            if (targetViewToRemove != null) {
                targetContainer.removeView(targetViewToRemove)
                recalculateContainerLayout(targetContainer)
            }
        }
    }

    private fun showAiDecompositionBottomSheet(todo: Todo) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val completedSteps = completedStepsMap.getOrPut(todo.id) { mutableSetOf() }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(24), dpToPx(20), dpToPx(24))
            setBackgroundColor(Color.WHITE)
        }

        val targetTaskIdLong = todo.id.toLongOrNull()
        val cleanTodoName = todo.name.trim().replace(" ", "").lowercase(Locale.KOREA)

        val matchedSchedules = currentGeneratedSchedules.filter { gen ->
            val isTaskIdMatch = targetTaskIdLong != null && gen.taskId == targetTaskIdLong
            val genTitleClean = gen.title.trim().replace(" ", "").lowercase(Locale.KOREA)
            val isTitleMatch = cleanTodoName.isNotEmpty() && (
                    genTitleClean.contains(cleanTodoName) || cleanTodoName.contains(genTitleClean)
                    )
            isTaskIdMatch || isTitleMatch
        }.sortedBy { it.stepOrder }

        val isNotDecomposed = todo.desiredSteps == 0

        val tvSubTitle = TextView(this).apply {
            text = if (isNotDecomposed) {
                "📝 단일 작업 계획\n[${todo.name}] (${if (todo.isCompleted) 1 else 0}/1단계 완료)"
            } else {
                "🤖 RePlan AI 추천 계획\n[${todo.name}] (${completedSteps.size}/${todo.desiredSteps}단계 완료)"
            }
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A237E"))
            setPadding(0, dpToPx(6), 0, dpToPx(16))
        }
        rootLayout.addView(tvSubTitle)

// showAiDecompositionBottomSheet() 내부

        val actualStepsList = if (isNotDecomposed) {
            listOf("1단계: [${todo.name}] ${todo.name}")
        } else if (matchedSchedules.isNotEmpty()) {
            matchedSchedules.mapIndexed { index, gen ->
                // 백엔드에서 넘어온 제목에서 기존 [태그] 제거 후 순수 텍스트만 추출
                val cleanTitle = gen.title.replace(Regex("\\[.*?\\]"), "").trim()

                // "N단계: [할 일 이름] 세부 내용" 포맷으로 조립
                "${index + 1}단계: [${todo.name}] $cleanTitle"
            }
        } else {
            List(todo.desiredSteps) { index -> "${index + 1}단계: [${todo.name}] ${todo.name}" }
        }

        actualStepsList.forEachIndexed { index, stepText ->
            val checkBox = CheckBox(this).apply {
                text = stepText
                textSize = 13f
                setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))

                val targetBlock = matchedSchedules.getOrNull(index)
                val isInitiallyChecked = todo.isCompleted || targetBlock?.completed == true || completedSteps.contains(index)

                if (isInitiallyChecked) {
                    completedSteps.add(index)
                }

                isChecked = isInitiallyChecked

                fun updateCheckUi(checked: Boolean) {
                    if (checked) {
                        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        setTextColor(Color.parseColor("#888888"))
                    } else {
                        paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        setTextColor(Color.parseColor("#333333"))
                    }
                }

                updateCheckUi(isChecked)

                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        completedSteps.add(index)
                    } else {
                        completedSteps.remove(index)
                    }

                    updateCheckUi(checked)

                    targetBlock?.let { gen ->
                        val updatedGen = gen.copy(completed = checked)
                        val updatedList = currentGeneratedSchedules.toMutableList()
                        val blockIdx = updatedList.indexOfFirst { it.blockId == gen.blockId }
                        if (blockIdx != -1) {
                            updatedList[blockIdx] = updatedGen
                            currentGeneratedSchedules = updatedList
                        }
                        requestUpdateScheduleStatus(gen = gen, completed = checked)
                    }

                    tvSubTitle.text = if (isNotDecomposed) {
                        "📝 단일 작업 계획\n[${todo.name}] (${if (checked) 1 else 0}/1단계 완료)"
                    } else {
                        "🤖 RePlan AI 추천 계획\n[${todo.name}] (${completedSteps.size}/${actualStepsList.size}단계 완료)"
                    }

                    val isAllDone = if (isNotDecomposed) checked else (completedSteps.size == actualStepsList.size)

                    val actualTodoInList = todoList.find { it.id == todo.id || it.name == todo.name } ?: todo
                    actualTodoInList.isCompleted = isAllDone

                    if (isAllDone) {
                        updateTodoCardCompletion(actualTodoInList, isAllCompleted = true)
                    } else {
                        updateTodoCardUncompletion(actualTodoInList)
                    }

                    if (::todoAdapter.isInitialized) {
                        todoAdapter.notifyDataSetChanged()
                    }
                }
            }
            rootLayout.addView(checkBox)
        }

        bottomSheetDialog.setContentView(rootLayout)
        bottomSheetDialog.show()
    }

    private fun updateTodoCardCompletion(targetTodo: Todo, isAllCompleted: Boolean) {
        val targetIndex = todoList.indexOfFirst { todo ->
            val isIdMatch = todo.id == targetTodo.id
            val isNameMatch = todo.name.trim().replace(" ", "").equals(targetTodo.name.trim().replace(" ", ""), ignoreCase = true)
            isIdMatch || isNameMatch
        }

        if (targetIndex != -1) {
            val todoItem = todoList[targetIndex]
            todoItem.isCompleted = isAllCompleted

            if (isAllCompleted) {
                todoList.removeAt(targetIndex)
                todoList.add(todoItem)

                lifecycleScope.launch {
                    try {
                        val taskIdLong = todoItem.id.toLongOrNull()
                        if (taskIdLong != null) {
                            ApiClient.service.updateTaskStatus(
                                taskId = taskIdLong,
                                request = UpdateTaskCompletionApiRequest(completed = true)
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                Toast.makeText(this, "'${todoItem.name}' 일정이 모두 완료되었습니다! 🎉", Toast.LENGTH_SHORT).show()
            } else {
                updateTodoCardUncompletion(targetTodo)
                return
            }

            if (::todoAdapter.isInitialized) {
                todoAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun updateTodoCardUncompletion(targetTodo: Todo) {
        val targetIndex = todoList.indexOfFirst { todo ->
            val isIdMatch = todo.id == targetTodo.id
            val isNameMatch = todo.name.trim().replace(" ", "").equals(targetTodo.name.trim().replace(" ", ""), ignoreCase = true)
            isIdMatch || isNameMatch
        }

        if (targetIndex != -1) {
            val todoItem = todoList[targetIndex]
            todoItem.isCompleted = false

            todoList.removeAt(targetIndex)
            todoList.add(0, todoItem)

            if (::todoAdapter.isInitialized) {
                todoAdapter.notifyDataSetChanged()
            }

            lifecycleScope.launch {
                try {
                    val taskIdLong = todoItem.id.toLongOrNull()
                    if (taskIdLong != null) {
                        ApiClient.service.updateTaskStatus(
                            taskId = taskIdLong,
                            request = UpdateTaskCompletionApiRequest(completed = false)
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun convertDayToKey(rawDayStr: String): String {
        if (rawDayStr.isBlank()) return "Mon"

        val upperRaw = rawDayStr.uppercase(Locale.KOREA)

        when {
            upperRaw.contains("MON") || upperRaw.contains("월") -> return "Mon"
            upperRaw.contains("TUE") || upperRaw.contains("화") -> return "Tue"
            upperRaw.contains("WED") || upperRaw.contains("수") -> return "Wed"
            upperRaw.contains("THU") || upperRaw.contains("목") -> return "Thu"
            upperRaw.contains("FRI") || upperRaw.contains("금") -> return "Fri"
            upperRaw.contains("SAT") || upperRaw.contains("토") -> return "Sat"
            upperRaw.contains("SUN") || upperRaw.contains("일") -> return "Sun"
        }

        try {
            val cleanDate = rawDayStr.replace(" ", "T").split("T")[0]
            val parts = cleanDate.split("-")
            if (parts.size == 3) {
                val cal = Calendar.getInstance(Locale.KOREA).apply {
                    set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
                return when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Mon"
                    Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"
                    Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"
                    Calendar.SATURDAY -> "Sat"
                    Calendar.SUNDAY -> "Sun"
                    else -> "Mon"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "Mon"
    }

    private fun convertDayToIsoDate(dayKey: String, timeStr: String = "10:00:00"): String {
        val calendar = currentWeekCalendar.clone() as Calendar
        calendar.firstDayOfWeek = Calendar.MONDAY

        val targetDayOfWeek = when (dayKey.uppercase(Locale.KOREA)) {
            "MON" -> Calendar.MONDAY
            "TUE" -> Calendar.TUESDAY
            "WED" -> Calendar.WEDNESDAY
            "THU" -> Calendar.THURSDAY
            "FRI" -> Calendar.FRIDAY
            "SAT" -> Calendar.SATURDAY
            "SUN" -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }

        calendar.set(Calendar.DAY_OF_WEEK, targetDayOfWeek)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val baseDateStr = sdf.format(calendar.time)

        val cleanTime = when {
            timeStr.length == 5 -> "$timeStr:00"
            timeStr.length == 8 -> timeStr
            else -> "10:00:00"
        }

        return "${baseDateStr}T${cleanTime}"
    }

    private fun clearAllContainers() {
        val dayKeys = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        dayKeys.forEach { key ->
            findViewSafely<LinearLayout>("container$key")?.removeAllViews()
        }
    }

    private fun simulateServerLoading(message: String, onComplete: () -> Unit) {
        if (isFinishing || isDestroyed) return

        val builder = AlertDialog.Builder(this)
        val layoutId = resources.getIdentifier("dialog_loading", "layout", packageName)

        if (layoutId != 0) {
            val dialogView = layoutInflater.inflate(layoutId, null)
            val tvMessage = dialogView.findViewById<TextView>(resources.getIdentifier("tvLoadingMessage", "id", packageName))
            tvMessage?.text = message
            builder.setView(dialogView)
        } else {
            builder.setMessage(message)
        }

        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed && dialog.isShowing) {
                dialog.dismiss()
            }
            onComplete()
        }, 800)
    }

    private fun setupDragAndDropContainers() {
        val dayKeys = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val containers = dayKeys.mapNotNull { key -> findViewSafely<LinearLayout>("container$key") }

        containers.forEach { container ->
            container.setOnDragListener { v, event ->
                val draggedView = event.localState as? View
                val targetContainer = v as? LinearLayout ?: return@setOnDragListener false
                val sourceContainer = draggedView?.parent as? LinearLayout

                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        targetContainer.setBackgroundColor(Color.parseColor("#E8EAF6"))
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        targetContainer.setBackgroundColor(Color.TRANSPARENT)
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        targetContainer.setBackgroundColor(Color.TRANSPARENT)
                        if (draggedView != null) {
                            sourceContainer?.removeView(draggedView)

                            val dropY = event.y
                            val rawMinutes = (dropY / (resources.displayMetrics.density * MINUTE_FACTOR)).toInt()
                            val snappedMinutes = ((rawMinutes + 15) / 30 * 30).coerceIn(0, 1380)

                            val tagData = (draggedView.tag as? ScheduleCardTag) ?: ScheduleCardTag()
                            val duration = (tagData.endMin - tagData.startMin).coerceAtLeast(30)

                            tagData.startMin = snappedMinutes
                            tagData.endMin = snappedMinutes + duration
                            draggedView.tag = tagData

                            targetContainer.addView(draggedView)
                            draggedView.visibility = View.VISIBLE

                            sourceContainer?.let { recalculateContainerLayout(it) }
                            recalculateContainerLayout(targetContainer)

                            val startHour = snappedMinutes / 60
                            val startMin = snappedMinutes % 60
                            val endHour = (snappedMinutes + duration) / 60
                            val endMin = (snappedMinutes + duration) % 60

                            val formattedStartTimeStr = String.format(Locale.KOREA, "%02d:%02d:00", startHour, startMin)
                            val formattedEndTimeStr = String.format(Locale.KOREA, "%02d:%02d:00", endHour, endMin)

                            val targetContainerId = resources.getResourceEntryName(targetContainer.id)
                            val dayKey = targetContainerId.replace("container", "").uppercase(Locale.KOREA)

                            val calculatedStartTimeIso = convertDayToIsoDate(dayKey, formattedStartTimeStr)
                            val calculatedEndTimeIso = convertDayToIsoDate(dayKey, formattedEndTimeStr)

                            val targetBlockId = tagData.blockId

                            if (!targetBlockId.isNullOrEmpty()) {
                                lifecycleScope.launch {
                                    try {
                                        val updateBody = UpdateScheduleStatusApiRequest(
                                            taskId = tagData.taskId,
                                            blockId = targetBlockId,
                                            locked = true,
                                            completed = null,
                                            startTime = calculatedStartTimeIso,
                                            endTime = calculatedEndTimeIso
                                        )

                                        ApiClient.service.updateGeneratedScheduleStatus(
                                            blockId = targetBlockId,
                                            request = updateBody
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            true
                        } else false
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        targetContainer.setBackgroundColor(Color.TRANSPARENT)
                        if (!event.result && draggedView != null) {
                            draggedView.post { draggedView.visibility = View.VISIBLE }
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun recalculateContainerLayout(container: LinearLayout) {
        val cardViews = mutableListOf<View>()

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child != null) {
                cardViews.add(child)
            }
        }

        cardViews.sortBy { childView ->
            val tagData = childView.tag as? ScheduleCardTag
            tagData?.startMin ?: 0
        }

        container.removeAllViews()

        var currentOffsetPx = 0

        cardViews.forEach { childView ->
            val tagData = (childView.tag as? ScheduleCardTag) ?: ScheduleCardTag()

            val startMin = tagData.startMin
            val endMin = tagData.endMin
            val durationMinutes = (endMin - startMin).coerceAtLeast(30)

            val targetStartPx = dpToPx((startMin * MINUTE_FACTOR).toInt())
            val marginTopPx = (targetStartPx - currentOffsetPx).coerceAtLeast(0)
            val cardHeightPx = dpToPx((durationMinutes * MINUTE_FACTOR).toInt())

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                cardHeightPx
            ).apply {
                topMargin = marginTopPx
            }

            childView.layoutParams = params
            container.addView(childView)

            currentOffsetPx = targetStartPx + cardHeightPx
        }
    }

    private fun enableDragAndDrop(view: View) {
        if (view is CardView && view.childCount > 0) {
            val childTextView = view.getChildAt(0)
            childTextView.isClickable = false
            childTextView.isFocusable = false
        }

        view.setOnLongClickListener { v ->
            val tagData = v.tag as? ScheduleCardTag

            val matchedGen = currentGeneratedSchedules.find { it.blockId == tagData?.blockId }
            if (matchedGen?.completed == true) {
                Toast.makeText(this, "이미 완료된 일정은 이동할 수 없습니다. 🔒", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            val textData = ((v as? CardView)?.getChildAt(0) as? TextView)?.text?.toString() ?: "일정"
            val item = ClipData.Item(textData)
            val dragData = ClipData(textData, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
            val shadowBuilder = View.DragShadowBuilder(v)
            val isStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(dragData, shadowBuilder, v, 0)
            } else {
                @Suppress("DEPRECATION")
                v.startDrag(dragData, shadowBuilder, v, 0)
            }
            if (isStarted) v.post { v.visibility = View.INVISIBLE }
            true
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}