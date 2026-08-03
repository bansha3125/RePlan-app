import json

from ai_scheduler.integration import schedule_api_from_payload


payload = {
    "tasks": [
        {
            "id": "task-1",
            "title": "발표 자료 조사",
            "deadline": "2026-08-03T18:00:00",
            "estimated_minutes": 60,
            "priority": 5,
            "difficulty": 3,
            "focus_required": 4,
            "postpone_count": 0,
            "prerequisite_ids": [],
            "category": "발표",
            "splittable": True,
            "min_block_minutes": 30,
            "max_block_minutes": 90,
            "completed_minutes": 0,
            "completed": False,
        }
    ],
    "existing_blocks": [
        {
            "block_id": "fixed-class-1",
            "title": "수업",
            "start": "2026-08-01T10:00:00",
            "end": "2026-08-01T12:00:00",
            "source": "fixed",
            "task_id": None,
            "locked": True,
            "completed": False,
            "reason": None,
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
    "now": "2026-08-01T09:00:00",
}


result = schedule_api_from_payload(payload)

print("\n========== 전체 결과 ==========")
print(
    json.dumps(
        result,
        ensure_ascii=False,
        indent=2,
    )
)

blocks = result.get("schedules", [])

if not blocks:
    print("\n생성된 일정이 없습니다.")
    print("warnings:", result.get("warnings"))
    raise SystemExit


first_block = blocks[0]

print("\n========== 첫 번째 일정 ==========")
print(
    json.dumps(
        first_block,
        ensure_ascii=False,
        indent=2,
    )
)


current_fields = [
    "block_id",
    "task_id",
    "title",
    "start",
    "end",
    "source",
    "locked",
    "reason",
]

print("\n========== 현재 필드 검사 ==========")

for field in current_fields:
    if field in first_block:
        print(f"[있음] {field}: {first_block[field]}")
    else:
        print(f"[없음] {field}")


desired_fields = [
    "blockId",
    "taskId",
    "title",
    "stepOrder",
    "startTime",
    "endTime",
    "source",
    "locked",
    "reasonCode",
    "reason",
]

print("\n========== 백엔드 최종 규격 검사 ==========")

for field in desired_fields:
    if field in first_block:
        print(f"[있음] {field}: {first_block[field]}")
    else:
        print(f"[없음] {field}")