package com.example.replan

import com.google.gson.annotations.SerializedName

// =========================================================================
// 1. 주간 일정 조회 응답 DTO (GET /schedules/weekly)
// =========================================================================

data class WeeklyScheduleResponse(
    @SerializedName("fixedSchedules") val fixedSchedules: List<FixedScheduleDto> = emptyList(),
    @SerializedName("generatedSchedules") val generatedSchedules: List<GeneratedScheduleDto> = emptyList()
)

data class FixedScheduleDto(
    @SerializedName("fixedScheduleId") val fixedScheduleId: Long? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("startTime") val startTime: String = "",
    @SerializedName("endTime") val endTime: String = "",
    @SerializedName("repeatDay") val repeatDay: String? = null,
    @SerializedName("locked") val locked: Boolean = true
)

data class GeneratedScheduleDto(
    @SerializedName("blockId") val blockId: String = "",
    @SerializedName("taskId") val taskId: Long? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("stepOrder") val stepOrder: Int = 1,
    @SerializedName("startTime") val startTime: String = "",
    @SerializedName("endTime") val endTime: String = "",
    @SerializedName("source") val source: String? = null,
    @SerializedName("locked") val locked: Boolean = false,
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("reasonCode") val reasonCode: String? = null,
    @SerializedName("reason") val reason: String? = null
)

// =========================================================================
// 2. 등록 및 상태 변경 요청 DTO
// =========================================================================

// 할 일 신규 등록 요청 DTO (POST /schedules/tasks)
data class CreateTaskApiRequest(
    @SerializedName("userId") val userId: Long = 1L,
    @SerializedName("title") val title: String,
    @SerializedName("deadline") val deadline: String? = "2026-08-31T23:59:59",
    @SerializedName("estimatedMinutes") val estimatedMinutes: Int,
    @SerializedName("priority") val priority: Int = 2, // 우선순위 (1: 상, 2: 중, 3: 하)
    @SerializedName("useAiDecomposition") val useAiDecomposition: Boolean = false,
    @SerializedName("desiredSteps") val desiredSteps: Int = 0,
    @SerializedName("deadlineType") val deadlineType: String? = null,
    @SerializedName("linkedScheduleId") val linkedScheduleId: Long? = null
)

// 고정 일정 신규 등록 요청 DTO (POST /schedules/fixed-schedules)
data class CreateFixedScheduleApiRequest(
    @SerializedName("userId") val userId: Long = 1L,
    @SerializedName("title") val title: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("repeat") val repeat: Boolean = false,
    @SerializedName("repeatDay") val repeatDay: String? = "MON"
)

// AI 스케줄 생성 요청 DTO (POST /schedules/generate)
data class GenerateScheduleApiRequest(
    @SerializedName("userId") val userId: Long = 1L,
    @SerializedName("weekStartDate") val weekStartDate: String? = null // 👈 [추가] 선택 주차 시작일 (예: "2026-08-10")
)

// AI 스케줄 재배치 요청 DTO (POST /schedules/replan)
data class ReplanApiRequest(
    @SerializedName("userId") val userId: Long = 1L,
    @SerializedName("replanFromTime") val replanFromTime: String,
    @SerializedName("completedTaskIds") val completedTaskIds: List<Long> = emptyList(),
    @SerializedName("postponedTaskIds") val postponedTaskIds: List<Long> = emptyList()
)

// 일정 상태 및 위치 업데이트 요청 DTO
data class UpdateScheduleStatusApiRequest(
    @SerializedName("taskId") val taskId: Long?,
    @SerializedName("blockId") val blockId: String?,
    @SerializedName("locked") val locked: Boolean? = null,
    @SerializedName("completed") val completed: Boolean? = null,
    @SerializedName("startTime") val startTime: String? = null,
    @SerializedName("endTime") val endTime: String? = null
)

// 할 일 조회 응답 DTO (GET /schedules/tasks - Task Entity 매핑)
data class TaskResponse(
    @SerializedName("taskId") val taskId: Long? = null,
    @SerializedName("userId") val userId: Long? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("deadline") val deadline: String? = null,
<<<<<<<< HEAD:app/src/main/java/com/example/replan/NetworkDto.kt
    @SerializedName("estimatedMinutes") val estimatedMinutes: Int = 120,
    @SerializedName("useAiDecomposition") val useAiDecomposition: Boolean = false,
    @SerializedName("desiredSteps") val desiredSteps: Int = 0,
    @SerializedName("priority") val priority: Int = 1,
    @SerializedName("difficulty") val difficulty: Int = 3,
    @SerializedName("focusRequired") val focusRequired: Int = 3,
    @SerializedName("postponeCount") val postponeCount: Int = 0,
    @SerializedName("completedMinutes") val completedMinutes: Int = 0,
    @SerializedName("completed") val completed: Boolean = false
========
    @SerializedName("priority") val priority: Int = 2,
    @SerializedName("desiredSteps") val desiredSteps: Int = 0
)

// AI 일정 재배치 요청 DTO (POST /schedules/replan)
data class ReplanScheduleApiRequest(
    @SerializedName("userId") val userId: Long,
    @SerializedName("replanFromTime") val replanFromTime: String,
    @SerializedName("completedTaskIds") val completedTaskIds: List<Long> = emptyList(),
    @SerializedName("postponedTaskIds") val postponedTaskIds: List<Long> = emptyList()
>>>>>>>> 670d784125d9e940ea9a6780ef12e3a9379eeb95:Front/app/src/main/java/com/example/replan/NetworkDto.kt
)