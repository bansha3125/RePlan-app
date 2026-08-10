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

    private var currentWeekCalendar: Calendar = Calendar.getInstance(Locale.KOREA).apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }

    private val MINUTE_FACTOR: Float = 75f / 120f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val today = Calendar.getInstance(Locale.KOREA)
        currentWeekCalendar = Calendar.getInstance(Locale.KOREA).apply {
            firstDayOfWeek = Calendar.MONDAY
            time = today.time
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }

        updateHeaderWeekRangeText()

        val rvTodoList = findViewSafely<RecyclerView>("rvTodoList")
        if (rvTodoList != null) {
            todoAdapter = TodoAdapter(todoList) { todo ->
                showAiDecompositionBottomSheet(todo)
            }
            rvTodoList.adapter = todoAdapter
            rvTodoList.layoutManager = LinearLayoutManager(this)
        }

        val fabAdd = findViewSafely<View>("fabAdd") ?: findViewSafely<View>("fab_add")
        fabAdd?.setOnClickListener { showAddTodoBottomSheet() }

        val btnAddFixed = findViewSafely<Button>("btnAddFixed")
        btnAddFixed?.setOnClickListener { showFixedScheduleBottomSheet() }

        val btnAutoSort = findViewSafely<Button>("btnAutoSort")
        btnAutoSort?.setOnClickListener { startAiReplayPipeline() }

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

        val targetY = dpToPx((480 * MINUTE_FACTOR).toInt()) // 08:00

        handler.postDelayed({
            targetScrollView?.let { sv ->
                if (sv is ScrollView) {
                    sv.smoothScrollTo(0, targetY)
                } else if (sv is NestedScrollView) {
                    sv.smoothScrollTo(0, targetY)
                }
            }
        }, 300)
    }

    private fun changeWeek(amount: Int) {
        currentWeekCalendar.add(Calendar.WEEK_OF_YEAR, amount)
        updateHeaderWeekRangeText()
        loadWeeklySchedulesFromServer()
    }

    private fun updateHeaderWeekRangeText() {
        val tvWeekRange = findViewSafely<TextView>("tvWeekRange")
        val startCal = currentWeekCalendar.clone() as Calendar
        val endCal = currentWeekCalendar.clone() as Calendar
        endCal.add(Calendar.DAY_OF_WEEK, 6)

        val startSdf = SimpleDateFormat("M월 d일", Locale.KOREA)
        val textStr = "${startSdf.format(startCal.time)} - ${startSdf.format(endCal.time)}"
        tvWeekRange?.text = textStr
    }

    private fun getSelectedWeekStartDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return sdf.format(currentWeekCalendar.time)
    }

    private fun getSelectedWeekSundayDeadline(): String {
        val cal = currentWeekCalendar.clone() as Calendar
        cal.add(Calendar.DAY_OF_WEEK, 6)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'23:59:59", Locale.KOREA)
        return sdf.format(cal.time)
    }

    private fun loadWeeklySchedulesFromServer() {
        simulateServerLoading("일정을 불러오는 중입니다...") {
            lifecycleScope.launch {
                try {
                    val response = ApiClient.service.getWeeklySchedules(
                        userId = 1L,
                        weekStartDate = getSelectedWeekStartDate()
                    )

                    currentFixedSchedules = response.fixedSchedules
                    currentGeneratedSchedules = response.generatedSchedules
                    clearAllContainers()

                    response.fixedSchedules.forEach { renderFixedScheduleCard(it) }
                    response.generatedSchedules.forEach { renderGeneratedScheduleCard(it) }

                    replayChanges = parseGeneratedSchedulesToChanges(response.generatedSchedules)
                    currentStepIndex = response.generatedSchedules.size - 1

                    val layoutReplayControl = findViewSafely<LinearLayout>("layoutReplayControl")
                    if (replayChanges.isNotEmpty()) {
                        layoutReplayControl?.visibility = View.VISIBLE
                    }

                    try {
                        val tasks = ApiClient.service.getTasks(userId = 1L)
                        val selectedSundayDate = getSelectedWeekSundayDeadline().split("T")[0]

                        todoList.clear()
                        if (tasks.isNotEmpty()) {
                            val weeklyTasks = tasks.filter { task ->
                                task.deadline?.contains(selectedSundayDate) == true || task.deadline == null
                            }

                            weeklyTasks.forEach { task ->
                                val priorityText = when (task.priority) {
                                    3 -> "상"
                                    2 -> "중"
                                    1 -> "하"
                                    else -> "중"
                                }

                                // ★ [보완] 해당 Task와 연관된 AI 일정의 개수를 파악하여 desiredSteps가 0이어도 정상 매핑
                                val matchedCount = response.generatedSchedules.count { gen ->
                                    gen.taskId == task.taskId || (task.title.isNotEmpty() && gen.title.contains(task.title))
                                }
                                val steps = if (task.desiredSteps > 0) task.desiredSteps else if (matchedCount > 0) matchedCount else 3

                                todoList.add(
                                    Todo(
                                        id = task.taskId?.toString() ?: java.util.UUID.randomUUID().toString(),
                                        name = task.title,
                                        deadlineType = "DATE",
                                        specificScheduleName = null,
                                        expectedTime = task.estimatedMinutes / 60,
                                        priority = priorityText,
                                        desiredSteps = steps,
                                        subSteps = emptyList()
                                    )
                                )
                            }
                        }
                        if (::todoAdapter.isInitialized) todoAdapter.notifyDataSetChanged()
                    } catch (e: Exception) {
                        Log.e("TASK_FETCH_EX", "getTasks 예외: ${e.message}")
                    }

                    scrollToMorningEight()

                    Toast.makeText(this@MainActivity, "일정을 불러왔습니다! ✨", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "일정 조회 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startAiReplayPipeline() {
        val btnAutoSort = findViewSafely<Button>("btnAutoSort")
        btnAutoSort?.isEnabled = false

        val tvAiFeedback = findViewSafely<TextView>("tvAiFeedback")
        tvAiFeedback?.text = "🤖 AI가 일정을 정리하고 있습니다..."

        simulateServerLoading("AI 일정을 생성하고 있습니다...") {
            lifecycleScope.launch {
                try {
                    val currentWeekStart = getSelectedWeekStartDate()
                    val apiResponse = ApiClient.service.generateSchedules(
                        GenerateScheduleApiRequest(
                            userId = 1L,
                            weekStartDate = currentWeekStart
                        )
                    )

                    if (apiResponse.isSuccessful) {
                        val updatedWeekly = ApiClient.service.getWeeklySchedules(
                            userId = 1L,
                            weekStartDate = currentWeekStart
                        )
                        currentFixedSchedules = updatedWeekly.fixedSchedules
                        currentGeneratedSchedules = updatedWeekly.generatedSchedules
                        handleGeneratedSchedulesResponse(updatedWeekly)
                    } else {
                        Toast.makeText(this@MainActivity, "AI 일정 생성 실패 (서버 오류)", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "일정 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    btnAutoSort?.isEnabled = true
                }
            }
        }
    }

    private fun handleGeneratedSchedulesResponse(response: WeeklyScheduleResponse) {
        val tvAiFeedback = findViewSafely<TextView>("tvAiFeedback")
        clearAllContainers()

        response.fixedSchedules.forEach { renderFixedScheduleCard(it) }
        response.generatedSchedules.forEach { renderGeneratedScheduleCard(it) }

        replayChanges = parseGeneratedSchedulesToChanges(response.generatedSchedules)

        val layoutReplayControl = findViewSafely<LinearLayout>("layoutReplayControl")
        layoutReplayControl?.visibility = View.VISIBLE

        stopAutoPlay()
        currentStepIndex = replayChanges.size - 1

        scrollToMorningEight()

        if (replayChanges.isNotEmpty()) {
            tvAiFeedback?.text = "🤖 AI가 ${replayChanges.size}개의 세부 일정 배치를 완료했습니다!"
            Toast.makeText(this@MainActivity, "AI 일정 자동 정렬 완료! ✨", Toast.LENGTH_SHORT).show()
        } else {
            tvAiFeedback?.text = "🤖 정리할 AI 일정을 찾을 수 없습니다."
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
        val selectedWeekStart = getSelectedWeekStartDate()
        val selectedSundayDate = getSelectedWeekSundayDeadline().split("T")[0]
        val cardDate = if (gen.startTime.contains("T")) gen.startTime.split("T")[0] else gen.startTime

        if (cardDate.isNotEmpty() && cardDate.contains("-")) {
            if (cardDate < selectedWeekStart || cardDate > selectedSundayDate) return
        }

        val dayKey = convertDayToKey(gen.startTime)
        val container = findViewSafely<LinearLayout>("container$dayKey") ?: return

        val startMin = parseTimeToMinutes(gen.startTime)
        val endMin = parseTimeToMinutes(gen.endTime)
        val durationMinutes = (endMin - startMin).coerceAtLeast(30)

        val cardHeightDp = (durationMinutes * MINUTE_FACTOR).toInt().coerceAtLeast(24)
        val cardBgColor = if (gen.completed) "#4CAF50" else if (gen.locked) "#1A237E" else "#283593"

        val totalBlocks = currentGeneratedSchedules.count { it.taskId == gen.taskId }

        val displayTitle = when {
            gen.title.startsWith("[") -> gen.title
            gen.stepOrder > 0 && totalBlocks > 1 -> "[${gen.stepOrder}/$totalBlocks]\n${gen.title}"
            gen.stepOrder > 0 -> "[${gen.stepOrder}단계]\n${gen.title}"
            else -> gen.title
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

    private fun showScheduleActionDialog(gen: GeneratedScheduleDto) {
        val options = arrayOf(
            if (gen.completed) "완료 취소" else "✔ 완료 처리",
            if (gen.locked) "고정 해제" else "🔒 일정 고정"
        )

        AlertDialog.Builder(this)
            .setTitle(gen.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestUpdateScheduleStatus(gen.taskId, gen.blockId, completed = !gen.completed)
                    1 -> requestUpdateScheduleStatus(gen.taskId, gen.blockId, locked = !gen.locked)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun requestUpdateScheduleStatus(
        taskId: Long?,
        blockId: String?,
        locked: Boolean? = null,
        completed: Boolean? = null,
        startTime: String? = null,
        endTime: String? = null
    ) {
        Log.d("DRAG_MOVE", "상태 변경: taskId=$taskId, blockId=$blockId, startTime=$startTime")
        Toast.makeText(this@MainActivity, "일정이 업데이트되었습니다! 📌", Toast.LENGTH_SHORT).show()
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

        var desiredSteps = 3 // 기본값 3단계 설정
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
            val expectedTimeStr = etExpectedTime?.text?.toString() ?: ""

            if (todoName.isEmpty()) {
                Toast.makeText(this, "할 일 이름을 입력해 주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val expectedMinutes = if (expectedTimeStr.isEmpty()) 120 else expectedTimeStr.toInt() * 60
            val priorityText = when (selectedPriorityInt) {
                3 -> "상"
                2 -> "중"
                1 -> "하"
                else -> "중"
            }

            val sundayDeadline = getSelectedWeekSundayDeadline()

            val apiRequest = CreateTaskApiRequest(
                userId = 1L,
                title = todoName,
                deadline = sundayDeadline,
                estimatedMinutes = expectedMinutes,
                priority = selectedPriorityInt,
                useAiDecomposition = (desiredSteps > 0),
                desiredSteps = desiredSteps
            )

            bottomSheetDialog.dismiss()

            simulateServerLoading("할 일을 등록하는 중입니다...") {
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.service.createTask(request = apiRequest)
                        if (response.isSuccessful) {
                            val newTodo = Todo(
                                id = java.util.UUID.randomUUID().toString(),
                                name = apiRequest.title,
                                deadlineType = "DATE",
                                specificScheduleName = null,
                                expectedTime = expectedMinutes / 60,
                                priority = priorityText,
                                desiredSteps = apiRequest.desiredSteps,
                                subSteps = emptyList()
                            )
                            todoList.add(newTodo)
                            if (::todoAdapter.isInitialized) todoAdapter.notifyDataSetChanged()
                            Toast.makeText(this@MainActivity, "'${apiRequest.title}' 등록 완료!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "저장 실패", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
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
            listOf("btnDayMon", "btnMon", "btnDay1", "btnMonDay") to "MON",
            listOf("btnDayTue", "btnTue", "btnDay2", "btnTueDay") to "TUE",
            listOf("btnDayWed", "btnWed", "btnDay3", "btnWedDay") to "WED",
            listOf("btnDayThu", "btnThu", "btnDay4", "btnThuDay") to "THU",
            listOf("btnDayFri", "btnFri", "btnDay5", "btnFriDay") to "FRI",
            listOf("btnDaySat", "btnSat", "btnDay6", "btnSatDay") to "SAT",
            listOf("btnDaySun", "btnSun", "btnDay7", "btnSunDay") to "SUN"
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
                                userId = 1L,
                                title = name,
                                startTime = targetDayIso,
                                endTime = targetEndDayIso,
                                repeat = true,
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
            if (currentStepIndex < currentGeneratedSchedules.size - 1) {
                currentStepIndex++
                applyReplayStepByDto(currentGeneratedSchedules[currentStepIndex], isForward = true)
            }
        }

        btnPrev?.setOnClickListener {
            if (currentStepIndex >= 0) {
                val dtoToRemove = currentGeneratedSchedules[currentStepIndex]
                currentStepIndex--
                applyReplayStepByDto(dtoToRemove, isForward = false)
            }
        }

        btnPlay?.setOnClickListener {
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
        clearAllContainers()

        currentFixedSchedules.forEach { renderFixedScheduleCard(it) }
        startAutoPlay()
    }

    private fun startAutoPlay() {
        isPlaying = true
        playRunnable = object : Runnable {
            override fun run() {
                if (currentStepIndex < currentGeneratedSchedules.size - 1) {
                    currentStepIndex++
                    applyReplayStepByDto(currentGeneratedSchedules[currentStepIndex], isForward = true)
                    handler.postDelayed(this, 1200)
                } else {
                    stopAutoPlay()
                    findViewSafely<Button>("btnReplayPlay")?.text = "▶ 재생"
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

    private fun applyReplayStepByDto(gen: GeneratedScheduleDto, isForward: Boolean) {
        val tvAiFeedback = findViewSafely<TextView>("tvAiFeedback")
        val dayKey = convertDayToKey(gen.startTime)
        val targetContainer = findViewSafely<LinearLayout>("container$dayKey") ?: return

        val totalBlocks = currentGeneratedSchedules.count { it.taskId == gen.taskId }
        val displayTitle = when {
            gen.title.startsWith("[") -> gen.title
            gen.stepOrder > 0 && totalBlocks > 1 -> "[${gen.stepOrder}/$totalBlocks]\n${gen.title}"
            gen.stepOrder > 0 -> "[${gen.stepOrder}단계]\n${gen.title}"
            else -> gen.title
        }

        if (isForward) {
            val stepSeq = currentStepIndex + 1
            tvAiFeedback?.text = "[STEP $stepSeq/${currentGeneratedSchedules.size}] $displayTitle\n💡 AI가 최적의 시간대로 배치했습니다."

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
            if (currentStepIndex >= 0) {
                val prevStep = currentGeneratedSchedules[currentStepIndex]
                val prevTotal = currentGeneratedSchedules.count { it.taskId == prevStep.taskId }
                val prevTitle = when {
                    prevStep.title.startsWith("[") -> prevStep.title
                    prevStep.stepOrder > 0 && prevTotal > 1 -> "[${prevStep.stepOrder}/$prevTotal]\n${prevStep.title}"
                    prevStep.stepOrder > 0 -> "[${prevStep.stepOrder}단계]\n${prevStep.title}"
                    else -> prevStep.title
                }

                tvAiFeedback?.text = "[STEP ${currentStepIndex + 1}/${currentGeneratedSchedules.size}] $prevTitle"
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

    // ★ [수정] 바텀시트 열릴 때 실제 생성된 세부 일정 단계 수 기준으로 유연하게 동기화
    private fun showAiDecompositionBottomSheet(todo: Todo) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val completedSteps = completedStepsMap.getOrPut(todo.id) { mutableSetOf() }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(24), dpToPx(20), dpToPx(24))
            setBackgroundColor(Color.WHITE)
        }

        val matchedSchedules = currentGeneratedSchedules.filter { gen ->
            val isTaskIdMatch = gen.taskId?.toString() == todo.id
            val isTitleMatch = gen.title.contains(todo.name, ignoreCase = true) || todo.name.contains(gen.title, ignoreCase = true)
            isTaskIdMatch || isTitleMatch
        }.sortedBy { it.stepOrder }

        val stepsCount = if (matchedSchedules.isNotEmpty()) matchedSchedules.size else if (todo.desiredSteps > 0) todo.desiredSteps else 1

        val tvSubTitle = TextView(this).apply {
            text = "🤖 RePlan AI 추천 계획\n[${todo.name}] (${completedSteps.size}/${stepsCount}단계 완료)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A237E"))
            setPadding(0, dpToPx(6), 0, dpToPx(16))
        }
        rootLayout.addView(tvSubTitle)

        val actualStepsList = if (matchedSchedules.isNotEmpty()) {
            matchedSchedules.map { it.title }
        } else {
            List(stepsCount) { index -> "${index + 1}단계: [${todo.name}] AI 추천 실행" }
        }

        actualStepsList.forEachIndexed { index, stepText ->
            val checkBox = CheckBox(this).apply {
                text = stepText
                textSize = 13f
                setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
                isChecked = completedSteps.contains(index)

                if (isChecked) {
                    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    setTextColor(Color.parseColor("#888888"))
                } else {
                    paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    setTextColor(Color.parseColor("#333333"))
                }

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) completedSteps.add(index) else completedSteps.remove(index)
                    tvSubTitle.text = "🤖 RePlan AI 추천 계획\n[${todo.name}] (${completedSteps.size}/${actualStepsList.size}단계 완료)"

                    if (isChecked) {
                        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        setTextColor(Color.parseColor("#888888"))
                    } else {
                        paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        setTextColor(Color.parseColor("#333333"))
                    }

                    val isAllDone = completedSteps.size == actualStepsList.size
                    updateTodoCardCompletion(todo, isAllDone)
                }
            }
            rootLayout.addView(checkBox)
        }

        bottomSheetDialog.setContentView(rootLayout)
        bottomSheetDialog.show()
    }

    private fun updateTodoCardCompletion(targetTodo: Todo, isAllCompleted: Boolean) {
        val index = todoList.indexOfFirst { it.id == targetTodo.id }
        if (index != -1) {
            val todoItem = todoList[index]
            todoItem.isCompleted = isAllCompleted

            if (isAllCompleted) {
                todoList.removeAt(index)
                todoList.add(todoItem)

                lifecycleScope.launch {
                    try {
                        val taskIdLong = targetTodo.id.toLongOrNull()
                        if (taskIdLong != null) {
                            ApiClient.service.updateTaskStatus(
                                taskId = taskIdLong,
                                completed = true
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                Toast.makeText(this, "'${targetTodo.name}' 일정이 모두 완료되었습니다! 🎉", Toast.LENGTH_SHORT).show()
            }

            if (::todoAdapter.isInitialized) {
                todoAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun convertDayToKey(rawDayStr: String): String {
        if (rawDayStr.isEmpty()) return "Mon"

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

        if (rawDayStr.contains("-")) {
            try {
                val datePart = rawDayStr.split("T")[0]
                val parts = datePart.split("-")
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
        val cleanTime = if (timeStr.length == 5) "$timeStr:00" else timeStr
        return "${sdf.format(calendar.time)}T$cleanTime"
    }

    private fun clearAllContainers() {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { key ->
            findViewSafely<LinearLayout>("container$key")?.removeAllViews()
        }
    }

    private fun parseGeneratedSchedulesToChanges(list: List<GeneratedScheduleDto>): List<ScheduleChangeDto> {
        return list.mapIndexed { index, dto ->
            ScheduleChangeDto(
                sequence = index + 1,
                action = if (dto.locked) "FIXED" else "MOVED",
                taskId = dto.taskId,
                blockId = dto.blockId,
                title = dto.title,
                beforeStartTime = null,
                beforeEndTime = null,
                afterStartTime = dto.startTime,
                afterEndTime = dto.endTime,
                reasonCode = dto.reasonCode ?: "OPTIMAL",
                reason = dto.reason ?: "💡 AI가 최적의 시간대로 배치했습니다."
            )
        }
    }

    private fun simulateServerLoading(message: String, onComplete: () -> Unit) {
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
            dialog.dismiss()
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
                    DragEvent.ACTION_DRAG_ENTERED -> { targetContainer.setBackgroundColor(Color.parseColor("#E8EAF6")); true }
                    DragEvent.ACTION_DRAG_EXITED -> { targetContainer.setBackgroundColor(Color.TRANSPARENT); true }
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

                            val hour = snappedMinutes / 60
                            val min = snappedMinutes % 60
                            val formattedTime = String.format(Locale.KOREA, "%02d:%02d:00", hour, min)

                            val targetContainerId = resources.getResourceEntryName(targetContainer.id)
                            val dayKey = targetContainerId.replace("container", "").uppercase()
                            val calculatedStartTime = convertDayToIsoDate(dayKey, formattedTime)

                            if (!tagData.blockId.isNullOrEmpty() || tagData.taskId != null) {
                                requestUpdateScheduleStatus(
                                    taskId = tagData.taskId,
                                    blockId = tagData.blockId,
                                    locked = true,
                                    startTime = calculatedStartTime
                                )
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
            container.addView(childView)

            val tagData = (childView.tag as? ScheduleCardTag) ?: ScheduleCardTag()

            val startMin = tagData.startMin
            val endMin = tagData.endMin
            val durationMinutes = (endMin - startMin).coerceAtLeast(30)

            val targetStartPx = dpToPx((startMin * MINUTE_FACTOR).toInt())
            val marginTopPx = (targetStartPx - currentOffsetPx).coerceAtLeast(0)
            val cardHeightPx = dpToPx((durationMinutes * MINUTE_FACTOR).toInt().coerceAtLeast(24))

            val params = (childView.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cardHeightPx)

            params.height = cardHeightPx
            params.topMargin = marginTopPx
            childView.layoutParams = params

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