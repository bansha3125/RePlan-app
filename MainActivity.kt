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

        setupDragAndDropContainers()
        setupReplayController()
        loadWeeklySchedulesFromServer()
    }

    private fun <T : View> findViewSafely(idName: String): T? {
        val id = resources.getIdentifier(idName, "id", packageName)
        return if (id != 0) findViewById(id) else null
    }

    private fun updateHeaderWeekRangeText() {
        val headerLayout = findViewSafely<LinearLayout>("headerLayout")
        if (headerLayout != null && headerLayout.childCount >= 2) {
            val tvWeekRange = headerLayout.getChildAt(1) as? TextView
            tvWeekRange?.text = getCurrentWeekRangeText()
        }
    }

    private fun loadWeeklySchedulesFromServer() {
        simulateServerLoading("일정을 불러오는 중입니다...") {
            lifecycleScope.launch {
                try {
                    val response = ApiClient.service.getWeeklySchedules(
                        userId = 1L,
                        weekStartDate = getCurrentWeekStartDate()
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

                    // 서버 DB의 Task 목록 안전 복원 연동
                    try {
                        val tasks = ApiClient.service.getTasks(userId = 1L)
                        Log.d("TASK_RESPONSE", "서버 수신 Task 개수: ${tasks.size}")

                        if (tasks.isNotEmpty()) {
                            todoList.clear()
                            tasks.forEach { task ->
                                todoList.add(
                                    Todo(
                                        id = task.taskId?.toString() ?: java.util.UUID.randomUUID().toString(),
                                        name = task.title,
                                        deadlineType = "DATE",
                                        specificScheduleName = null,
                                        expectedTime = task.estimatedMinutes / 60,
                                        priority = if (task.priority == 1) "높음" else "중",
                                        desiredSteps = task.desiredSteps,
                                        subSteps = emptyList()
                                    )
                                )
                            }
                            if (::todoAdapter.isInitialized) todoAdapter.notifyDataSetChanged()
                        }
                    } catch (taskEx: Exception) {
                        Log.e("TASK_FETCH_EX", "getTasks 불러오기 예외: ${taskEx.message}")
                    }

                    Toast.makeText(this@MainActivity, "주간 일정을 불러왔습니다! ✨", Toast.LENGTH_SHORT).show()
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
                    Log.d("AI_CALL", "백엔드 AI 스케줄링 API(generateSchedules) Query Param으로 호출")
                    val response = ApiClient.service.generateSchedules(userId = 1L)

                    Log.d("AI_RESPONSE", "AI 정렬 fixed: ${response.fixedSchedules.size}, generated: ${response.generatedSchedules.size}")

                    currentGeneratedSchedules = response.generatedSchedules
                    handleGeneratedSchedulesResponse(response)

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

        // 🚀 [핵심 추가] AI 스케줄링 완료 직후 하단 '이번주 할 일' 목록도 서버에서 동시에 당겨와 갱신합니다!
        lifecycleScope.launch {
            try {
                val tasks = ApiClient.service.getTasks(userId = 1L)
                if (tasks.isNotEmpty()) {
                    todoList.clear()
                    tasks.forEach { task ->
                        todoList.add(
                            Todo(
                                id = task.taskId?.toString() ?: java.util.UUID.randomUUID().toString(),
                                name = task.title,
                                deadlineType = "DATE",
                                specificScheduleName = null,
                                expectedTime = task.estimatedMinutes / 60,
                                priority = if (task.priority == 1) "높음" else "중",
                                desiredSteps = task.desiredSteps,
                                subSteps = emptyList()
                            )
                        )
                    }
                    if (::todoAdapter.isInitialized) todoAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e("TASK_SYNC_EX", "자동정렬 후 Task 동기화 실패: ${e.message}")
            }
        }
    }


    private fun renderFixedScheduleCard(fixed: FixedScheduleDto) {
        val dayKey = convertDayToKey(fixed.repeatDay ?: fixed.startTime)
        val container = findViewSafely<LinearLayout>("container$dayKey") ?: return

        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(50)
            ).apply { setMargins(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4)) }
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
            textSize = 10f
            gravity = Gravity.CENTER
        }
        card.addView(tv)
        enableDragAndDrop(card)
        container.addView(card)
    }

    private fun renderGeneratedScheduleCard(gen: GeneratedScheduleDto) {
        val dayKey = convertDayToKey(gen.startTime)
        val container = findViewSafely<LinearLayout>("container$dayKey") ?: return

        val cardBgColor = if (gen.completed) "#4CAF50" else if (gen.locked) "#1A237E" else "#283593"

        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)
            ).apply { setMargins(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4)) }
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
            text = "$doneTag$lockTag${gen.title}\n(AI 추천)"
            setTextColor(Color.WHITE)
            textSize = 10f
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
        simulateServerLoading("변경된 일정을 반영하고 있습니다...") {
            lifecycleScope.launch {
                try {
                    val requestDto = UpdateScheduleStatusApiRequest(
                        taskId = taskId,
                        blockId = blockId,
                        locked = locked,
                        completed = completed,
                        startTime = startTime,
                        endTime = endTime
                    )

                    Log.d("DRAG_CHECK_REQ", "전송 DTO: $requestDto")

                    ApiClient.service.updateScheduleStatus(requestDto)
                    Log.d("DRAG_CHECK_RES", "서버 응답 성공")

                    Toast.makeText(this@MainActivity, "일정 위치가 저장되었습니다! ✨", Toast.LENGTH_SHORT).show()

                    Handler(Looper.getMainLooper()).postDelayed({
                        loadWeeklySchedulesFromServer()
                    }, 300)

                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("DRAG_CHECK_EX", "통신 에러: ${e.message}")
                    Toast.makeText(this@MainActivity, "저장 실패: 네트워크 연결 확인", Toast.LENGTH_SHORT).show()
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

            val apiRequest = CreateTaskApiRequest(
                title = todoName,
                estimatedMinutes = expectedMinutes,
                deadline = getCurrentFormattedDateTime(endOfDay = true),
                priority = selectedPriorityInt,
                desiredSteps = desiredSteps
            )

            bottomSheetDialog.dismiss()

            simulateServerLoading("할 일을 등록하는 중입니다...") {
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.service.createTask(userId = 1L, request = apiRequest)

                        if (response.isSuccessful) {
                            // 🚀 [수정] 시간표에 자동정렬을 즉시 수행하지 않고, 하단 '이번 주 할 일' 목록에만 반영합니다!
                            val newTodo = Todo(
                                id = java.util.UUID.randomUUID().toString(),
                                name = apiRequest.title,
                                deadlineType = "DATE",
                                specificScheduleName = null,
                                expectedTime = expectedMinutes / 60,
                                priority = if (selectedPriorityInt == 1) "높음" else "중",
                                desiredSteps = apiRequest.desiredSteps,
                                subSteps = emptyList()
                            )
                            todoList.add(newTodo)
                            if (::todoAdapter.isInitialized) todoAdapter.notifyDataSetChanged()

                            Toast.makeText(this@MainActivity, "'${apiRequest.title}' 등록 완료! '✨ 자동정렬' 버튼을 누르면 시간표에 배치됩니다.", Toast.LENGTH_LONG).show()
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

        var selectedDay = "MON"

        btnRegister?.setOnClickListener {
            val name = etScheduleName?.text?.toString()?.trim() ?: ""
            val startTime = etStartTime?.text?.toString()?.trim() ?: "10:00"
            val endTime = etEndTime?.text?.toString()?.trim() ?: "12:00"

            if (name.isEmpty()) {
                Toast.makeText(this, "일정 이름을 입력해 주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val targetDayIso = convertDayToIsoDate(selectedDay)
            val baseDate = targetDayIso.split("T")[0]

            val apiRequest = CreateFixedScheduleApiRequest(
                title = name,
                startTime = "${baseDate}T$startTime:00",
                endTime = "${baseDate}T$endTime:00",
                repeatDay = selectedDay
            )

            bottomSheetDialog.dismiss()

            simulateServerLoading("고정 일정을 저장하는 중입니다...") {
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.service.createFixedSchedule(userId = 1L, request = apiRequest)

                        if (response.isSuccessful) {
                            Toast.makeText(this@MainActivity, "[${apiRequest.repeatDay}] '${apiRequest.title}' 등록 완료!", Toast.LENGTH_SHORT).show()
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
                    setMargins(dpToPx(2), dpToPx(4), dpToPx(2), dpToPx(4))
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
                textSize = 10f
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

        // 세부 AI 일정 명칭 매핑 로직 강화 (단어 매핑 및 taskId 비교 포함)
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
        if (rawDayStr.contains("-") && rawDayStr.contains("T")) {
            try {
                val datePart = rawDayStr.split("T")[0]
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
                val date = sdf.parse(datePart)
                if (date != null) {
                    val cal = Calendar.getInstance()
                    cal.time = date
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

    private fun getCurrentWeekRangeText(): String {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val startSdf = SimpleDateFormat("M월 d일", Locale.KOREA)
        val startDateStr = startSdf.format(calendar.time)

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDateStr = startSdf.format(calendar.time)

        return "$startDateStr - $endDateStr"
    }

    private fun getCurrentWeekStartDate(): String {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return sdf.format(calendar.time)
    }

    private fun getCurrentFormattedDateTime(endOfDay: Boolean = false): String {
        val calendar = Calendar.getInstance()
        val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val dateStr = dateSdf.format(calendar.time)
        return if (endOfDay) "${dateStr}T23:59:59" else "${dateStr}T10:00:00"
    }

    private fun convertDayToIsoDate(dayKey: String): String {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY

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