package com.example.replan

import com.google.gson.annotations.SerializedName

// =========================================================================
// 1. 주간 일정 조회 응답 DTO
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

// 일반 할 일 추가 (POST /schedules/tasks)
data class CreateTaskApiRequest(
    @SerializedName("userId") val userId: Long = 1L, // ★ 백엔드 필수 필드 추가
    @SerializedName("title") val title: String,
    @SerializedName("deadline") val deadline: String? = null,
    @SerializedName("estimatedMinutes") val estimatedMinutes: Int,
    @SerializedName("useAiDecomposition") val useAiDecomposition: Boolean = false,
    @SerializedName("desiredSteps") val desiredSteps: Int = 0,
    @SerializedName("deadlineType") val deadlineType: String? = "DATE",
    @SerializedName("linkedScheduleId") val linkedScheduleId: Long? = null,
    @SerializedName("priority") val priority: Int? = 2
)

// 고정 일정 추가 (POST /schedules/fixed-schedules)
data class CreateFixedScheduleApiRequest(
    @SerializedName("userId") val userId: Long = 1L, // ★ 백엔드 필수 필드 추가
    @SerializedName("title") val title: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("repeat") val repeat: Boolean = false,
    @SerializedName("repeatDay") val repeatDay: String = "MONDAY"
)

// AI 스케줄 생성 요청 (POST /schedules/generate)
data class GenerateScheduleApiRequest(
    @SerializedName("userId") val userId: Long = 1L, // ★ 백엔드 필수 필드 추가
    @SerializedName("weekStartDate") val weekStartDate: String? = null
)

// AI 스케줄 재배치 요청 (POST /schedules/replan)
data class ReplanApiRequest(
    @SerializedName("userId") val userId: Long = 1L, // ★ 백엔드 필수 필드 추가
    @SerializedName("replanFromTime") val replanFromTime: String,
    @SerializedName("completedTaskIds") val completedTaskIds: List<Long> = emptyList(),
    @SerializedName("postponedTaskIds") val postponedTaskIds: List<Long> = emptyList()
)

// 일정 상태 및 위치 변경 (PATCH /schedules/blocks/{blockId})
data class UpdateScheduleStatusApiRequest(
    @SerializedName("userId") val userId: Long = 1L, // ★ 백엔드 필수 필드 추가
    @SerializedName("taskId") val taskId: Long? = null,
    @SerializedName("blockId") val blockId: String? = null,
    @SerializedName("locked") val locked: Boolean? = null,
    @SerializedName("completed") val completed: Boolean? = null,
    @SerializedName("startTime") val startTime: String? = null,
    @SerializedName("endTime") val endTime: String? = null
)

// 할 일 조회 응답 DTO
// 할 일 조회 응답 DTO
// TaskResponse (할 일 조회 DTO) 수정
data class TaskResponse(
    @SerializedName("taskId") val taskId: Long? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("deadline") val deadline: String? = null,
    @SerializedName("estimatedMinutes") val estimatedMinutes: Int = 120,
    @SerializedName("useAiDecomposition") val useAiDecomposition: Boolean? = true, // ★ null 대응 기본값 true
    @SerializedName("desiredSteps") val desiredSteps: Int? = 3,                    // ★ null 대응 기본값 3
    @SerializedName("priority") val priority: Int = 2,
    @SerializedName("difficulty") val difficulty: Int = 3,
    @SerializedName("focusRequired") val focusRequired: Int = 3,
    @SerializedName("postponeCount") val postponeCount: Int = 0,
    @SerializedName("completedMinutes") val completedMinutes: Int = 0,
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("deadlineType") val deadlineType: String? = "DATE",
    @SerializedName("linkedScheduleId") val linkedScheduleId: Long? = null
)

// Task 완료 상태 변경 요청 Body DTO
data class UpdateTaskCompletionApiRequest(
    @SerializedName("completed") val completed: Boolean
)

// =========================================================================
// 3. UI 및 애니메이션 리플레이용 내부 데이터 모델
// =========================================================================

data class ScheduleCardTag(
    val taskId: Long? = null,
    val blockId: String? = null,
    val fixedScheduleId: Long? = null,
    val type: String = "AI",
    val locked: Boolean = false,
    var startMin: Int = 0,
    var endMin: Int = 60
)