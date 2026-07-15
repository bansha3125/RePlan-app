
\
from ai_scheduler.scheduler import schedule_tasks

from ai_scheduler.gemini_service import GeminiAssistant


gemini = GeminiAssistant()

from pprint import pprint

from ai_scheduler.integration import (
    decompose_and_schedule_from_payload,
)



payload = {
    "parent_task_id": "ai-presentation",
    "task_title": "AI 경진대회 발표 준비",
    "desired_steps": 5,
    "total_estimated_minutes": 180,
    "deadline": "2026-07-25T23:59:00",
    "priority": 5,
    "category": "AI 경진대회",
    "context": (
        "자체 스케줄링 알고리즘과 Gemini 기능을 "
        "설명하는 발표를 준비한다."
    ),
    "now": "2026-07-20T08:00:00",

    "existing_blocks": [
        {
            "block_id": "class-python",
            "title": "파이썬 수업",
            "start": "2026-07-20T10:00:00",
            "end": "2026-07-20T12:00:00",
            "source": "fixed",
            "locked": True,
        }
    ],

    "preferences": {
        "day_start": "09:00",
        "day_end": "22:00",
        "slot_minutes": 30,
        "focus_start": "09:00",
        "focus_end": "12:00",
        "max_daily_generated_minutes": 480,
    },
}


result = decompose_and_schedule_from_payload(
    payload=payload,
    gemini_service=gemini,
)


print("[1] Gemini 작업 분해")
pprint(result["decomposition"])


print("\n[2] 생성된 Task")
pprint(result["generated_tasks"])


print("\n[3] 자동 생성 일정")
pprint(result["schedule"])