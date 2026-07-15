from pprint import pprint

from ai_scheduler.gemini_service import GeminiAssistant


assistant = GeminiAssistant()


print("\n[1] 큰 작업 자동 분해 테스트")
decomposed = assistant.decompose_task(
    task_title="AI 경진대회 발표 준비",
    desired_steps=5,
    total_estimated_minutes=180,
)

pprint(decomposed)

reason = assistant.recommendation_reason(
    task_title="파이썬 1주차 코드 짜기",
    scheduled_time="2026-07-20 10:30~12:00",
    deadline="2026-07-22 12:00",
    score_components={
        "urgency": 88,
        "priority": 50,
        "postponement": 0,
        "focus_and_difficulty": 20,
        "total": 166,
    },
)

print(reason)

message = assistant.postponement_message(
    task_title="파이썬 실습 문제 추가 풀이",
    postpone_count=3,
    next_scheduled_time="2026-07-23 09:00~10:00",
)

print(message)

print("\n[4] 사용자 맞춤형 시간 관리 피드백 테스트")
feedback = assistant.personalized_feedback(
    feedback_data={
        "completion_rate": 68,
        "repeated_postponements": [
            {
                "task_title": "파이썬 실습 문제 추가 풀이",
                "postpone_count": 3,
            }
        ],
        "average_actual_minutes_by_category": {
            "과제": 110,
            "복습": 70,
            "개발": 150,
        },
        "missed_task_patterns": {
            "common_category": "개발",
            "common_hour": 21,
        },
    }
)

print(feedback)