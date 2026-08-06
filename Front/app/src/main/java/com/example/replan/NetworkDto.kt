package com.example.replan

import com.google.gson.annotations.SerializedName

// =========================================================================
// 주간 일정 조회 관련 DTO (GET /schedules/weekly)
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
    @SerializedName("locked") val locked: Boolean = false,
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("reasonCode") val reasonCode: String? = null,
    @SerializedName("reason") val reason: String? = null
)

// =========================================================================
// 등록 및 상태 변경 요청 DTO
// =========================================================================

// 할 일 신규 등록 요청 DTO (POST /schedules/tasks)
data class CreateTaskApiRequest(
    @SerializedName("title") val title: String,
    @SerializedName("estimatedMinutes") val estimatedMinutes: Int,
    @SerializedName("deadline") val deadline: String? = "2026-08-31T23:59:59",
    @SerializedName("priority") val priority: Int = 2,
    @SerializedName("difficulty") val difficulty: Int = 3,
    @SerializedName("focusRequired") val focusRequired: Int = 3,
    @SerializedName("desiredSteps") val desiredSteps: Int = 0
)

// 고정 일정 신규 등록 요청 DTO (POST /schedules/fixed-schedules)
data class CreateFixedScheduleApiRequest(
    @SerializedName("title") val title: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("repeatDay") val repeatDay: String? = "MON"
)

// 일정 상태 및 위치 업데이트 요청 DTO (POST /schedules/status)
data class UpdateScheduleStatusApiRequest(
    @SerializedName("taskId") val taskId: Long?,
    @SerializedName("blockId") val blockId: String?,
    @SerializedName("locked") val locked: Boolean? = null,
    @SerializedName("completed") val completed: Boolean? = null,
    @SerializedName("startTime") val startTime: String? = null,
    @SerializedName("endTime") val endTime: String? = null
)

data class GenerateScheduleApiRequest(
    @SerializedName("userId") val userId: Long = 1L
)

// 할 일 조회 응답 DTO (GET /schedules/tasks)
data class TaskResponse(
    @SerializedName("taskId") val taskId: Long? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("estimatedMinutes") val estimatedMinutes: Int = 120,
    @SerializedName("deadline") val deadline: String? = null,
    @SerializedName("priority") val priority: Int = 2,
    @SerializedName("desiredSteps") val desiredSteps: Int = 0
)

data class ReplanScheduleApiRequest(
    val userId: Long,
    val replanFromTime: String,
    val completedTaskIds: List<Long> = emptyList(),
    val postponedTaskIds: List<Long> = emptyList()
)