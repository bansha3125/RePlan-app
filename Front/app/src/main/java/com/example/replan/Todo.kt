package com.example.replan

// 📝 앱 내 Todo 아이템 모델
data class Todo(
    val id: String,
    var name: String,
    val deadlineType: String?,
    val specificScheduleName: String?,
    var expectedTime: Int,
    var priority: String,
    var isCompleted: Boolean = false,
    var desiredSteps: Int = 3,
    val subSteps: List<String> = emptyList()
)