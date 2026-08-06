package com.example.replan

import com.google.gson.annotations.SerializedName

data class FixedScheduleRequest(
    @SerializedName("title") val title: String,        // 일정 제목
    @SerializedName("dayOfWeek") val dayOfWeek: String, // 요일 (월, 화, 수...)
    @SerializedName("startTime") val startTime: String, // 시작 시간 (예: "14:00")
    @SerializedName("endTime") val endTime: String      // 종료 시간 (예: "15:00")
)