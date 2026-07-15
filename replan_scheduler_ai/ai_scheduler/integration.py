from __future__ import annotations

from datetime import datetime, time
from typing import Any

from .models import CalendarBlock, SchedulePreferences, Task
from .scheduler import schedule_tasks


def _dt(value: str) -> datetime:
    return datetime.fromisoformat(value)


def _time(value: str) -> time:
    return time.fromisoformat(value)

def tasks_from_gemini_steps(
    steps: list[dict[str, Any]],
    parent_task_id: str,
    deadline: datetime,
    priority: int = 3,
    category: str = "기타",
) -> list[Task]:
    """
    Gemini 작업 분해 결과를 스케줄링용 Task 객체 목록으로 변환한다.
    """

    if not steps:
        return []

    task_id_by_order: dict[int, str] = {}

    # Gemini의 order 값을 실제 Task ID와 연결
    for step in steps:
        order = int(step["order"])
        task_id_by_order[order] = f"{parent_task_id}-step-{order}"

    tasks: list[Task] = []

    for step in steps:
        order = int(step["order"])
        depends_on_order = step.get("depends_on_order")

        prerequisite_ids: list[str] = []

        if depends_on_order is not None:
            depends_on_order = int(depends_on_order)

            prerequisite_id = task_id_by_order.get(depends_on_order)

            if prerequisite_id is not None:
                prerequisite_ids.append(prerequisite_id)

        task = Task(
            id=task_id_by_order[order],
            title=str(step["title"]),
            deadline=deadline,
            estimated_minutes=max(
                1,
                int(step["estimated_minutes"]),
            ),
            priority=max(1, min(int(priority), 5)),
            difficulty=max(
                1,
                min(int(step.get("difficulty", 3)), 5),
            ),
            focus_required=max(
                1,
                min(int(step.get("focus_required", 3)), 5),
            ),
            prerequisite_ids=prerequisite_ids,
            category=category,
            splittable=True,
        )

        tasks.append(task)

    return tasks

def schedule_from_payload(payload: dict[str, Any]) -> dict[str, Any]:
    """
    백엔드 연결용 함수.
    dict/JSON 입력을 받아 dict/JSON 출력으로 반환한다.
    """
    tasks = [
        Task(
            id=str(item["id"]),
            title=item["title"],
            deadline=_dt(item["deadline"]),
            estimated_minutes=int(item["estimated_minutes"]),
            priority=int(item.get("priority", 3)),
            difficulty=int(item.get("difficulty", 3)),
            focus_required=int(item.get("focus_required", 3)),
            postpone_count=int(item.get("postpone_count", 0)),
            prerequisite_ids=[str(value) for value in item.get("prerequisite_ids", [])],
            category=item.get("category", "기타"),
            course_id=item.get("course_id"),
            week_order=item.get("week_order"),
            task_type=item.get("task_type", "general"),
            lecture_start=_dt(item["lecture_start"])
            if item.get("lecture_start")
            else None,
            splittable=bool(item.get("splittable", True)),
            min_block_minutes=int(item.get("min_block_minutes", 30)),
            max_block_minutes=int(item.get("max_block_minutes", 90)),
            completed_minutes=int(item.get("completed_minutes", 0)),
            completed=bool(item.get("completed", False)),
        )
        for item in payload.get("tasks", [])
    ]

    blocks = [
        CalendarBlock(
            block_id=str(item["block_id"]),
            title=item["title"],
            start=_dt(item["start"]),
            end=_dt(item["end"]),
            source=item.get("source", "fixed"),
            task_id=str(item["task_id"]) if item.get("task_id") is not None else None,
            locked=bool(item.get("locked", True)),
            completed=bool(item.get("completed", False)),
            reason=item.get("reason"),
        )
        for item in payload.get("existing_blocks", [])
    ]

    pref = payload.get("preferences", {})
    preferences = SchedulePreferences(
        day_start=_time(pref.get("day_start", "09:00")),
        day_end=_time(pref.get("day_end", "22:00")),
        slot_minutes=int(pref.get("slot_minutes", 30)),
        focus_start=_time(pref.get("focus_start", "09:00")),
        focus_end=_time(pref.get("focus_end", "12:00")),
        max_daily_generated_minutes=int(
            pref.get("max_daily_generated_minutes", 480)
        ),
    )

    now = _dt(payload["now"]) if payload.get("now") else None
    return schedule_tasks(tasks, blocks, preferences, now).to_dict()

def decompose_and_schedule_from_payload(
    payload: dict[str, Any],
    gemini_service: Any,
) -> dict[str, Any]:
    """
    백엔드 JSON 입력을 받아
    Gemini 작업 분해 → Task 변환 → 일정 자동 배치를 수행한다.
    """

    required_fields = [
        "parent_task_id",
        "task_title",
        "total_estimated_minutes",
        "deadline",
    ]

    missing_fields = [
        field_name
        for field_name in required_fields
        if field_name not in payload
    ]

    if missing_fields:
        raise ValueError(
            f"필수 입력값이 없습니다: {', '.join(missing_fields)}"
        )

    parent_task_id = str(payload["parent_task_id"])
    task_title = str(payload["task_title"]).strip()

    desired_steps = max(
        1,
        min(int(payload.get("desired_steps", 5)), 10),
    )

    total_estimated_minutes = int(
        payload["total_estimated_minutes"]
    )

    deadline = _dt(payload["deadline"])

    priority = max(
        1,
        min(int(payload.get("priority", 3)), 5),
    )

    category = str(payload.get("category", "기타"))
    context = str(payload.get("context", ""))

    # 1. Gemini 큰 작업 분해
    decomposition_result = gemini_service.decompose_task(
        task_title=task_title,
        desired_steps=desired_steps,
        total_estimated_minutes=total_estimated_minutes,
        context=context,
    )

    steps = decomposition_result.data.get("steps", [])

    if not steps:
        raise RuntimeError(
            "Gemini 작업 분해 결과에 steps가 없습니다."
        )

    # 2. Gemini 결과를 Task 객체로 변환
    tasks = tasks_from_gemini_steps(
        steps=steps,
        parent_task_id=parent_task_id,
        deadline=deadline,
        priority=priority,
        category=category,
    )

    # 3. 기존 일정 변환
    blocks = [
        CalendarBlock(
            block_id=str(item["block_id"]),
            title=item["title"],
            start=_dt(item["start"]),
            end=_dt(item["end"]),
            source=item.get("source", "fixed"),
            task_id=(
                str(item["task_id"])
                if item.get("task_id") is not None
                else None
            ),
            locked=bool(item.get("locked", True)),
            completed=bool(item.get("completed", False)),
            reason=item.get("reason"),
        )
        for item in payload.get("existing_blocks", [])
    ]

    # 4. 사용자 일정 설정
    pref = payload.get("preferences", {})

    preferences = SchedulePreferences(
        day_start=_time(
            pref.get("day_start", "09:00")
        ),
        day_end=_time(
            pref.get("day_end", "22:00")
        ),
        slot_minutes=int(
            pref.get("slot_minutes", 30)
        ),
        focus_start=_time(
            pref.get("focus_start", "09:00")
        ),
        focus_end=_time(
            pref.get("focus_end", "12:00")
        ),
        max_daily_generated_minutes=int(
            pref.get(
                "max_daily_generated_minutes",
                480,
            )
        ),
    )

    now = (
        _dt(payload["now"])
        if payload.get("now")
        else None
    )

    # 5. 자체 스케줄링 알고리즘 실행
    schedule_result = schedule_tasks(
        tasks,
        blocks,
        preferences,
        now,
    )

    schedule_data = schedule_result.to_dict()

    # 6. 백엔드 반환 JSON 생성
    return {
        "decomposition": {
            "steps": steps,
            "used_fallback": decomposition_result.used_fallback,
            "error": decomposition_result.error,
        },
        "generated_tasks": [
            {
                "id": task.id,
                "title": task.title,
                "deadline": task.deadline.isoformat(),
                "estimated_minutes": task.estimated_minutes,
                "priority": task.priority,
                "difficulty": task.difficulty,
                "focus_required": task.focus_required,
                "prerequisite_ids": task.prerequisite_ids,
                "category": task.category,
            }
            for task in tasks
        ],
        "schedule": schedule_data,
    }