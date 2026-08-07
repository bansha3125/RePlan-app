package com.example.replan

import com.google.gson.annotations.SerializedName

// 백엔드 AI 일정 변경 및 자동정렬 전체 응답 데이터
data class ReplayResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("previewId") val previewId: String,
    @SerializedName("changes") val changes: List<ScheduleChangeDto>,
    @SerializedName("finalSchedules") val finalSchedules: List<FinalScheduleDto>
)

// 재생(Replay) 단계별 변경 사항 데이터
data class ScheduleChangeDto(
    @SerializedName("sequence") val sequence: Int,            // 재생 순서 (1, 2, 3...)
    @SerializedName("action") val action: String,              // CREATED, MOVED, KEPT, SPLIT
    @SerializedName("taskId") val taskId: Long?,
    @SerializedName("blockId") val blockId: String,
    @SerializedName("title") val title: String,                // 일정 제목
    @SerializedName("beforeStartTime") val beforeStartTime: String?,
    @SerializedName("beforeEndTime") val beforeEndTime: String?,
    @SerializedName("afterStartTime") val afterStartTime: String?,
    @SerializedName("afterEndTime") val afterEndTime: String?,
    @SerializedName("reasonCode") val reasonCode: String,
    @SerializedName("reason") val reason: String               // 화면에 보여줄 배치의 근거 문장
)

// 최종 완성된 주간 일정 데이터
data class FinalScheduleDto(
    @SerializedName("blockId") val blockId: String,
    @SerializedName("title") val title: String,
    @SerializedName("dayOfWeek") val dayOfWeek: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("isAiGenerated") val isAiGenerated: Boolean
)