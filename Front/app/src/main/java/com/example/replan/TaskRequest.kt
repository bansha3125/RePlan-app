package com.example.replan

import com.google.gson.annotations.SerializedName

data class TaskRequest(
    @SerializedName("name") val name: String,                  // 할 일 이름
    @SerializedName("deadlineType") val deadlineType: String,  // 마감일 유형 (DATE, SCHEDULE, NONE)
    @SerializedName("specificScheduleName") val specificScheduleName: String?, // 특정 일정 전 마감 시 일정명
    @SerializedName("expectedTime") val expectedTime: Int,      // 예상 소요 시간
    @SerializedName("desiredSteps") val desiredSteps: Int       // AI 작업 분해 단계 수 (0, 3, 5, 7)
)