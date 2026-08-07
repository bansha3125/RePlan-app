package com.example.replan

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val USE_MOCK_DATA = false

    private val todoList = mutableListOf<Todo>()
    private lateinit var todoAdapter: TodoAdapter

    // 서버에서 응답받은 AI 배치 일정을 보관하여 쪼개기 팝업에서 참조
    private var currentGeneratedSchedules = listOf<GeneratedScheduleDto>()

    private var replayChanges = listOf<ScheduleChangeDto>()
    private var currentStepIndex = -1
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private var playRunnable: Runnable? = null

    private val completedStepsMap = mutableMapOf<String, MutableSet<Int>>()

    // 현재 선택된 주차 기준 날짜 (기본값: 이번 주 월요일)
    private var currentWeekCalendar: Calendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        fabAdd?.setOnClickListener {
            showAddTodoBottomSheet()
        }

        val btnAddFixed = findViewSafely<Button>("btnAddFixed")
        btnAddFixed?.setOnClickListener {
            showFixedScheduleBottomSheet()
        }

        val btnAutoSort = findViewSafely<Button>("btnAutoSort")
        btnAutoSort?.setOnClickListener {
            startAiReplayPipeline()
        }

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

                    Log.d("SERVER_RESPONSE", "주간 조회 fixed: ${response.fixedSchedules.size}, generated: ${response.generatedSchedules.size}")

                    currentGeneratedSchedules = response.generatedSchedules

                    clearAllContainers()

                    response.fixedSchedules.forEach { fixed ->
                        renderFixedScheduleCard(fixed)
                    }

                    response.generatedSchedules.forEach { gen ->
                        renderGeneratedScheduleCard(gen)
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
                                    1 -> "높음"
                                    2 -> "중"
                                    3 -> "낮음"
                                    else -> "중"
                                }
                                todoList.add(
                                    Todo(
                                        id = task.taskId?.toString() ?: java.util.UUID.randomUUID().toString(),
                                        name = task.title,
                                        deadlineType = "DATE",
                                        specificScheduleName = null,
                                        expectedTime = task.estimatedMinutes / 60,
                                        priority = priorityText,
                                        desiredSteps = task.desiredSteps,
                                        subSteps = emptyList()
                                    )
                                )
                            }
                        }
                        if (::todoAdapter.isInitialized) todoAdapter.notifyDataSetChanged()
                    } catch (taskEx: Exception) {
                        Log.e("TASK_FETCH_EX", "getTasks 불러오기 예외: ${taskEx.message}")
                    }

                    Toast.makeText(this@MainActivity, "일정을 불러왔습니다! ✨", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "일정 조회 실패: 네트워크 연결을 확인하세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startAiReplayPipeline() {
        val btnAutoSort = findViewSafely<Button>("btnAutoSort")
        btnAutoSort?.isEnabled = false

        val tvAiFeedback = findViewSafely<TextView>("tvAiFeedback")
        tvAiFeedback?.text = "🤖 AI가 일정을 정리하고 있습니다. 잠시만 기다려 주세요..."

        simulateServerLoading("AI 일정을 생성하고 있습니다...") {
            lifecycleScope.launch {
                try {
                    val currentWeekStart = getSelectedWeekStartDate()
                    Log.d("AI_CALL", "백엔드 AI 스케줄링 API(generateSchedules) - 대상 주차: $currentWeekStart")

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
                        currentGeneratedSchedules = updatedWeekly.generatedSchedules
                        handleGeneratedSchedulesResponse(updatedWeekly)
                    } else {
                        Toast.makeText(this@MainActivity, "AI 일정 생성 실패 (서버 오류)", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("AI_CALL_EX", "AI 통신 예외: ${e.message}")
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

        currentStepIndex = -1
        if (replayChanges.isNotEmpty()) {
            tvAiFeedback?.text = "🤖 AI가 ${replayChanges.size}개의 세부 일정 배치를 완료했습니다!"
            Toast.makeText(this@MainActivity, "AI 일정 자동 정렬 완료! ✨", Toast.LENGTH_SHORT).show()
        } else {
            tvAiFeedback?.text = "🤖 정리할 AI 일정을 찾을 수 없습니다."
        }

        lifecycleScope.launch {
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
                            1 -> "높음"
                            2 -> "중"
                            3 -> "낮음"
                            else -> "중"
                        }
                        todoList.add(
                            Todo(
                                id = task.taskId?.toString() ?: java.util.UUID.randomUUID().toString(),
                                name = task.title,
                                deadlineType = "DATE",
                                specificScheduleName = null,
                                expectedTime = task.estimatedMinutes / 60,
                                priority = priorityText,
                                desiredSteps = task.desiredSteps,
                                subSteps = emptyList()
                            )
                        )
                    }
                }
                if (::todoAdapter.isInitialized) todoAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("TASK_SYNC_EX", "자동정렬 후 Task 동기화 실패: ${e.message}")
            }
        }
    }

    private fun calculateCardHeightDp(startTimeStr: String, endTimeStr: String): Int {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.KOREA)
            val startDate = format.parse(startTimeStr.substring(0, 16))
            val endDate = format.parse(endTimeStr.substring(0, 16))

            if (startDate != null && endDate != null) {
                val diffMinutes = ((endDate.time - startDate.time) / (1000 * 60)).toInt()
                return when {
                    diffMinutes <= 30 -> 44
                    diffMinutes <= 60 -> 68
                    diffMinutes <= 90 -> 100
                    diffMinutes <= 120 -> 130
                    else -> 160
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 56
    }

    private fun renderFixedScheduleCard(fixed: FixedScheduleDto) {
        val dayKey = convertDayToKey(fixed.repeatDay ?: fixed.startTime)
        val container = findViewSafely<LinearLayout>("container$dayKey") ?: return
        val cardHeightDp = calculateCardHeightDp(fixed.startTime, fixed.endTime)

        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(cardHeightDp)
            ).apply { setMargins(dpToPx(1), dpToPx(3), dpToPx(1), dpToPx(3)) }
            radius = dpToPx(8).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#E0E0E0"))

            tag = mapOf(
                "fixedScheduleId" to fixed.fixedScheduleId,
                "locked" to fixed.locked,
                "type" to "FIXED"
            )
        }

        val tv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            text = "[고정]\n${fixed.title}"
            setTextColor(Color.parseColor("#424242"))
            textSize = 9.5f
            gravity = Gravity.CENTER
        }
        card.addView(tv)
        enableDragAndDrop(card)
        container.addView(card)
    }

    private fun renderGeneratedScheduleCard(gen: GeneratedScheduleDto) {
        val selectedWeekStart = getSelectedWeekStartDate()
        val selectedSundayDate = getSelectedWeekSundayDeadline().split("T")[0]

        val cardDate = if (gen.startTime.contains("T")) gen.startTime.split("T")[0] else gen.startTime

        if (cardDate.isNotEmpty() && cardDate.contains("-")) {
            if (cardDate < selectedWeekStart || cardDate > selectedSundayDate) {
                return
            }
        }

        val dayKey = convertDayToKey(gen.startTime)
        val container = findViewSafely<LinearLayout>("container$dayKey") ?: return

        val cardHeightDp = calculateCardHeightDp(gen.startTime, gen.endTime)

        val cardBgColor = if (gen.completed) "#4CAF50" else if (gen.locked) "#1A237E" else "#283593"

        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(cardHeightDp)
            ).apply { setMargins(dpToPx(1), dpToPx(3), dpToPx(1), dpToPx(3)) }
            radius = dpToPx(8).toFloat()
            cardElevation = 2f
            setCardBackgroundColor(Color.parseColor(cardBgColor))

            tag = mapOf(
                "taskId" to gen.taskId,
                "blockId" to gen.blockId,
                "stepOrder" to gen.stepOrder,
                "locked" to gen.locked,
                "completed" to gen.completed,
                "type" to "AI"
            )
        }

        val tv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            val lockTag = if (gen.locked) "🔒 " else ""
            val doneTag = if (gen.completed) "✔ " else ""

            text = "$doneTag$lockTag${gen.title}"
            setTextColor(Color.WHITE)
            textSize = 9.5f
            gravity = Gravity.CENTER
        }
        card.addView(tv)

        card.setOnClickListener {
            showScheduleActionDialog(gen)
        }

        enableDragAndDrop(card)
        container.addView(card)
    }

    private fun showScheduleActionDialog(gen: GeneratedScheduleDto) {
        val options = arrayOf(
            if (gen.completed) "완료 취소" else "✔ 완료 처리 (taskId: ${gen.taskId})",
            if (gen.locked) "고정 해제" else "🔒 일정 고정 (blockId: ${gen.blockId})"
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
        Log.d("DRAG_MOVE", "이동 완료: taskId=$taskId, blockId=$blockId, startTime=$startTime")
        Toast.makeText(this@MainActivity, "일정 위치가 조정되었습니다! 📌", Toast.LENGTH_SHORT).show()
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
        priorityButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                selectedPriorityInt = index + 1
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

        var desiredSteps = 0
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

            val expectedMinutes = if (expectedTimeStr.isEmpty()) 120 else expectedTimeStr.toInt() * 60

            val priorityText = when (selectedPriorityInt) {
                1 -> "높음"
                2 -> "중"
                3 -> "낮음"
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

                            Toast.makeText(
                                this@MainActivity,
                                "'${apiRequest.title}' 등록 완료!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(this@MainActivity, "저장 실패 (서버 응답 오류)", Toast.LENGTH_SHORT).show()
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

    // 🚀 [수정] 복수 요일 선택 및 입력 시간(HH:mm) 정확 보정 등록 로직
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

        val btnMon = findInView<Button>("btnDayMon") ?: findInView<Button>("btnMon")
        val btnTue = findInView<Button>("btnDayTue") ?: findInView<Button>("btnTue")
        val btnWed = findInView<Button>("btnDayWed") ?: findInView<Button>("btnWed")
        val btnThu = findInView<Button>("btnDayThu") ?: findInView<Button>("btnThu")
        val btnFri = findInView<Button>("btnDayFri") ?: findInView<Button>("btnFri")
        val btnSat = findInView<Button>("btnDaySat") ?: findInView<Button>("btnSat")
        val btnSun = findInView<Button>("btnDaySun") ?: findInView<Button>("btnSun")

        val dayButtons = listOf(
            btnMon?.let { it to "MON" },
            btnTue?.let { it to "TUE" },
            btnWed?.let { it to "WED" },
            btnThu?.let { it to "THU" },
            btnFri?.let { it to "FRI" },
            btnSat?.let { it to "SAT" },
            btnSun?.let { it to "SUN" }
        ).filterNotNull()

        // 🚀 복수 요일 선택을 위한 집합 (Set)
        val selectedDays = mutableSetOf<String>("MON")

        dayButtons.forEach { (btn, dayCode) ->
            btn.setOnClickListener {
                if (selectedDays.contains(dayCode)) {
                    if (selectedDays.size > 1) { // 최소 1개는 선택 유지
                        selectedDays.remove(dayCode)
                        btn.setBackgroundColor(Color.parseColor("#D1D1D6"))
                        btn.setTextColor(Color.parseColor("#333333"))
                    }
                } else {
                    selectedDays.add(dayCode)
                    btn.setBackgroundColor(Color.parseColor("#283593"))
                    btn.setTextColor(Color.WHITE)
                }
            }
        }

        // 입력된 시간 텍스트 정규화 ("9:00" -> "09:00:00")
        fun formatTimeString(rawTime: String, defaultTime: String): String {
            val trimmed = rawTime.trim()
            if (trimmed.isEmpty()) return "$defaultTime:00"
            val parts = trimmed.split(":")
            return when (parts.size) {
                1 -> {
                    val hour = parts[0].padStart(2, '0')
                    "$hour:00:00"
                }
                2 -> {
                    val hour = parts[0].padStart(2, '0')
                    val min = parts[1].padStart(2, '0')
                    "$hour:$min:00"
                }
                else -> "$defaultTime:00"
            }
        }

        btnRegister?.setOnClickListener {
            val name = etScheduleName?.text?.toString()?.trim() ?: ""
            val rawStartTime = etStartTime?.text?.toString() ?: ""
            val rawEndTime = etEndTime?.text?.toString() ?: ""

            if (name.isEmpty()) {
                Toast.makeText(this, "일정 이름을 입력해 주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val formattedStartTime = formatTimeString(rawStartTime, "10:00")
            val formattedEndTime = formatTimeString(rawEndTime, "12:00")

            bottomSheetDialog.dismiss()

            simulateServerLoading("고정 일정을 저장하는 중입니다...") {
                lifecycleScope.launch {
                    try {
                        var successCount = 0
                        // 🚀 선택된 여러 요일에 대해 각각 생성 API 호출
                        selectedDays.forEach { dayCode ->
                            val targetDayIso = convertDayToIsoDate(dayCode)
                            val baseDate = targetDayIso.split("T")[0]

                            val apiRequest = CreateFixedScheduleApiRequest(
                                userId = 1L,
                                title = name,
                                startTime = "${baseDate}T$formattedStartTime",
                                endTime = "${baseDate}T$formattedEndTime",
                                repeatDay = dayCode
                            )

                            val response = ApiClient.service.createFixedSchedule(request = apiRequest)
                            if (response.isSuccessful) successCount++
                        }

                        if (successCount > 0) {
                            Toast.makeText(this@MainActivity, "'$name' 고정 일정 ${successCount}개 요일 등록 완료! ✨", Toast.LENGTH_SHORT).show()
                            loadWeeklySchedulesFromServer()
                        } else {
                            Toast.makeText(this@MainActivity, "등록 실패 (서버 응답 오류)", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity, "등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
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
            if (currentStepIndex < replayChanges.size - 1) {
                currentStepIndex++
                applyReplayStep(replayChanges[currentStepIndex])
            }
        }
        btnPrev?.setOnClickListener {
            if (currentStepIndex > 0) {
                currentStepIndex--
                applyReplayStep(replayChanges[currentStepIndex])
            }
        }
        btnPlay?.setOnClickListener {
            if (isPlaying) {
                stopAutoPlay()
                btnPlay.text = "▶ 재생"
            } else {
                if (currentStepIndex >= replayChanges.size - 1) currentStepIndex = -1
                btnPlay.text = "⏸ 정지"
                startAutoPlay()
            }
        }
    }

    private fun startAutoPlay() {
        isPlaying = true
        playRunnable = object : Runnable {
            override fun run() {
                if (currentStepIndex < replayChanges.size - 1) {
                    currentStepIndex++
                    applyReplayStep(replayChanges[currentStepIndex])
                    handler.postDelayed(this, 1800)
                } else {
                    stopAutoPlay()
                    val btnPlay = findViewSafely<Button>("btnReplayPlay")
                    btnPlay?.text = "▶ 재생"
                    Toast.makeText(this@MainActivity, "모든 AI 일정 배치가 완료되었습니다! ✨", Toast.LENGTH_SHORT).show()
                }
            }
        }
        handler.post(playRunnable!!)
    }

    private fun stopAutoPlay() {
        isPlaying = false
        playRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun applyReplayStep(change: ScheduleChangeDto) {
        val tvAiFeedback = findViewSafely<TextView>("tvAiFeedback")
        tvAiFeedback?.text = "[STEP ${change.sequence}] ${change.title}\n${change.reason}"

        val dayKey = convertDayToKey(change.afterStartTime ?: "MON")
        val targetContainer = findViewSafely<LinearLayout>("container$dayKey") ?: return

        var isAlreadyExist = false
        for (i in 0 until targetContainer.childCount) {
            val card = targetContainer.getChildAt(i) as? CardView
            val tv = card?.getChildAt(0) as? TextView
            if (tv?.text?.contains(change.title) == true) {
                isAlreadyExist = true
                break
            }
        }

        if (!isAlreadyExist) {
            val newCard = CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)).apply {
                    setMargins(dpToPx(1), dpToPx(3), dpToPx(1), dpToPx(3))
                }
                radius = dpToPx(8).toFloat()
                cardElevation = 2f
                setCardBackgroundColor(Color.parseColor("#283593"))

                tag = mapOf(
                    "blockId" to change.blockId,
                    "taskId" to change.taskId.toString(),
                    "type" to "AI"
                )
            }
            val newTextView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                text = "${change.title}\n(AI 추천)"
                setTextColor(Color.WHITE)
                textSize = 9.5f
                gravity = Gravity.CENTER
            }
            newCard.addView(newTextView)
            enableDragAndDrop(newCard)
            targetContainer.addView(newCard)
        }
    }

    private fun showAiDecompositionBottomSheet(todo: Todo) {
        val stepsCount = if (todo.desiredSteps > 0) todo.desiredSteps else 1
        val bottomSheetDialog = BottomSheetDialog(this)
        val completedSteps = completedStepsMap.getOrPut(todo.id) { mutableSetOf() }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(24), dpToPx(20), dpToPx(24))
            setBackgroundColor(Color.WHITE)
        }

        val tvSubTitle = TextView(this).apply {
            text = "🤖 RePlan AI 추천 계획\n[${todo.name}] (${completedSteps.size}/${stepsCount}단계 완료)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A237E"))
            setPadding(0, dpToPx(6), 0, dpToPx(16))
        }
        rootLayout.addView(tvSubTitle)

        val matchedSchedules = if (todo.subSteps.isNotEmpty()) {
            emptyList()
        } else {
            currentGeneratedSchedules.filter { gen ->
                val isTaskIdMatch = gen.taskId?.toString() == todo.id
                val isTitleMatch = gen.title.contains(todo.name, ignoreCase = true) ||
                        todo.name.contains(gen.title, ignoreCase = true) ||
                        todo.name.split(" ").any { word -> word.length > 1 && gen.title.contains(word, ignoreCase = true) }
                isTaskIdMatch || isTitleMatch
            }.sortedBy { it.stepOrder }
        }

        val matchedTitles = if (todo.subSteps.isNotEmpty()) {
            todo.subSteps
        } else {
            matchedSchedules.map { it.title }
        }

        val actualStepsList = if (matchedTitles.isNotEmpty()) {
            matchedTitles
        } else {
            List(stepsCount) { index -> "${index + 1}단계: [${todo.name}] AI 추천 세부 실행" }
        }

        actualStepsList.take(stepsCount).forEachIndexed { index, stepText ->
            val checkBox = CheckBox(this).apply {
                text = stepText
                textSize = 13f
                isChecked = completedSteps.contains(index)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) completedSteps.add(index) else completedSteps.remove(index)
                    tvSubTitle.text = "🤖 RePlan AI 추천 계획\n[${todo.name}] (${completedSteps.size}/${actualStepsList.size}단계 완료)"
                }
            }
            rootLayout.addView(checkBox)
        }

        bottomSheetDialog.setContentView(rootLayout)
        bottomSheetDialog.show()
    }

    private fun convertDayToKey(rawDayStr: String): String {
        if (rawDayStr.isEmpty()) return "Mon"

        if (rawDayStr.contains("-")) {
            try {
                val datePart = rawDayStr.split("T")[0]
                val parts = datePart.split("-")
                if (parts.size == 3) {
                    val year = parts[0].toInt()
                    val month = parts[1].toInt() - 1
                    val day = parts[2].toInt()

                    val cal = Calendar.getInstance().apply {
                        set(year, month, day)
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

        return when {
            rawDayStr.contains("MON", true) || rawDayStr.contains("월") -> "Mon"
            rawDayStr.contains("TUE", true) || rawDayStr.contains("화") -> "Tue"
            rawDayStr.contains("WED", true) || rawDayStr.contains("수") -> "Wed"
            rawDayStr.contains("THU", true) || rawDayStr.contains("목") -> "Thu"
            rawDayStr.contains("FRI", true) || rawDayStr.contains("금") -> "Fri"
            rawDayStr.contains("SAT", true) || rawDayStr.contains("토") -> "Sat"
            rawDayStr.contains("SUN", true) || rawDayStr.contains("일") -> "Sun"
            else -> "Mon"
        }
    }

    private fun getCurrentFormattedDateTime(endOfDay: Boolean = false): String {
        val calendar = Calendar.getInstance()
        val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val dateStr = dateSdf.format(calendar.time)
        return if (endOfDay) "${dateStr}T23:59:59" else "${dateStr}T10:00:00"
    }

    private fun convertDayToIsoDate(dayKey: String): String {
        val calendar = currentWeekCalendar.clone() as Calendar

        val targetDayOfWeek = when (dayKey) {
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
        val formattedDate = sdf.format(calendar.time)

        return "${formattedDate}T10:00:00"
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
                reason = dto.reason ?: "💡 AI가 가장 몰입도가 높은 시간대로 배치했습니다."
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
        }, 1000)
    }

    private fun setupDragAndDropContainers() {
        val dayKeys = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val containers = dayKeys.mapNotNull { key -> findViewSafely<LinearLayout>("container$key") }

        containers.forEach { container ->
            container.setOnDragListener { v, event ->
                val draggedView = event.localState as? View
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> { v.setBackgroundColor(Color.parseColor("#E8EAF6")); true }
                    DragEvent.ACTION_DRAG_EXITED -> { v.setBackgroundColor(Color.TRANSPARENT); true }
                    DragEvent.ACTION_DROP -> {
                        v.setBackgroundColor(Color.TRANSPARENT)
                        if (draggedView != null) {
                            (draggedView.parent as? LinearLayout)?.removeView(draggedView)
                            (v as LinearLayout).addView(draggedView)
                            draggedView.visibility = View.VISIBLE

                            val targetContainerId = resources.getResourceEntryName(v.id)
                            val dayKey = targetContainerId.replace("container", "").uppercase()

                            val calculatedStartTime = convertDayToIsoDate(dayKey)
                            val calculatedEndTime = convertDayToIsoDate(dayKey).replace("T10:00:00", "T11:00:00")

                            val tagMap = draggedView.tag as? Map<*, *>
                            val taskIdRaw = tagMap?.get("taskId")
                            val taskIdLong = when (taskIdRaw) {
                                is Long -> taskIdRaw
                                is String -> taskIdRaw.toLongOrNull()
                                else -> null
                            }
                            val blockId = tagMap?.get("blockId")?.toString() ?: ""

                            if (blockId.isNotEmpty() || taskIdLong != null) {
                                requestUpdateScheduleStatus(
                                    taskId = taskIdLong,
                                    blockId = blockId,
                                    locked = true,
                                    startTime = calculatedStartTime,
                                    endTime = calculatedEndTime
                                )
                            }
                            true
                        } else false
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        v.setBackgroundColor(Color.TRANSPARENT)
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