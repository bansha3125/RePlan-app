package com.example.replan

// 📝 앱 내 Todo 아이템 모델
data class Todo(
    val id: String,                         // 구분용 고유 ID
    val name: String,                       // 할 일 이름
    val deadlineType: String,               // 마감 기준 ("DATE" / "SCHEDULE" / "NONE")
    val specificScheduleName: String? = null, // 특정 일정 연동 이름
    val expectedTime: Int,                  // 예상 소요 시간 (시간 단위)
    val priority: String,                   // 우선순위 ("상", "중", "하")
    val desiredSteps: Int = 0,              // AI 작업 분해 단계수 (0, 3, 5, 7)
    val subSteps: List<String> = emptyList(),
    var isCompleted: Boolean = false
)