package com.example.replan

// 📝 할 일 데이터를 담는 상자 (패키지 경로가 동일해야 MainActivity에서 찾을 수 있어!)
data class Todo(
    val id: String,                 // 구분용 고유 ID
    val name: String,               // 할 일 이름
    val deadlineType: String,       // 마감 기준 ("DATE" / "SCHEDULE" / "NONE")
    val specificScheduleName: String?, // "SCHEDULE"일 때 어떤 일정 전인지 이름
    val expectedTime: Int,          // 예상 소요 시간 (시간 단위)
    val priority: String,           // 우선순위 ("상", "중", "하")
    val desiredSteps: Int,           // AI 작업 분해 단계수 (0, 3, 5, 7)
    val subSteps: List<String> = emptyList()
)