

from datetime import datetime, time

from ai_scheduler.models import SchedulePreferences
from ai_scheduler.scheduler import schedule_tasks

from ai_scheduler.gemini_service import GeminiAssistant
from ai_scheduler.integration import tasks_from_gemini_steps


gemini = GeminiAssistant()


print("[1] Gemini 큰 작업 분해")

result = gemini.decompose_task(
    task_title="AI 경진대회 발표 준비",
    desired_steps=5,
    total_estimated_minutes=180,
)

print(result)


print("\n[2] Gemini 결과 → Task 객체 변환")

tasks = tasks_from_gemini_steps(
    steps=result.data["steps"],
    parent_task_id="ai-presentation",
    deadline=datetime.fromisoformat(
        "2026-07-25T23:59:00"
    ),
    priority=5,
    category="AI 경진대회",
)


for task in tasks:
    print(
        f"""
ID: {task.id}
작업명: {task.title}
예상 시간: {task.estimated_minutes}분
우선순위: {task.priority}
난이도: {task.difficulty}
집중 필요도: {task.focus_required}
선행 작업: {task.prerequisite_ids}
"""


    )

print("\n[3] Task 객체 → 스케줄링 알고리즘 자동 배치")

preferences = SchedulePreferences(
    day_start=time(9, 0),
    day_end=time(22, 0),
    slot_minutes=30,
    focus_start=time(9, 0),
    focus_end=time(12, 0),
    max_daily_generated_minutes=480,
)

schedule_result = schedule_tasks(
    tasks=tasks,
    existing_blocks=[],
    preferences=preferences,
    now=datetime.fromisoformat("2026-07-20T08:00:00"),
)

schedule_data = schedule_result.to_dict()

print(schedule_data)