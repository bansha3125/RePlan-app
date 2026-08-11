package com.example.replan

// 📝 앱 내 Todo 아이템 모델
// Todo.kt 파일 수정 예시
data class Todo(
    val id: String,
    var name: String,                  // 👈 val -> var 로 변경!
    val deadlineType: String,
    val specificScheduleName: String?,
    var expectedTime: Int,             // 👈 val -> var 로 변경!
    var priority: String,              // 👈 val -> var 로 변경!
    var isCompleted: Boolean = false,  // 👈 var 유지
    var desiredSteps: Int = 3,         // 👈 필요시 var 로 변경
    val subSteps: List<String> = emptyList()
)