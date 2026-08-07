from datetime import datetime, time
from pprint import pprint

from ai_scheduler import (
    CalendarBlock,
    SchedulePreferences,
    Task,
    schedule_tasks,
)


now = datetime(2026, 7, 1, 8, 0)
preferences = SchedulePreferences(
    day_start=time(9, 0),
    day_end=time(22, 0),
    focus_start=time(9, 0),
    focus_end=time(12, 0),
)

tasks = [
    Task(
        id="review-13",
        title="영어회화 13주차 복습",
        deadline=datetime(2026, 7, 7, 14, 0),
        lecture_start=datetime(2026, 7, 7, 14, 0),
        estimated_minutes=60,
        priority=4,
        difficulty=3,
        focus_required=4,
        course_id="english",
        week_order=13,
        task_type="review",
    ),
    Task(
        id="python-project",
        title="파이썬 프로젝트",
        deadline=datetime(2026, 7, 4, 23, 0),
        estimated_minutes=240,
        priority=5,
        difficulty=5,
        focus_required=5,
        max_block_minutes=90,
    ),
    Task(
        id="slides",
        title="발표 PPT 제작",
        deadline=datetime(2026, 7, 6, 18, 0),
        estimated_minutes=120,
        priority=4,
        prerequisite_ids=["research"],
    ),
    Task(
        id="research",
        title="발표 자료 조사",
        deadline=datetime(2026, 7, 5, 18, 0),
        estimated_minutes=90,
        priority=3,
    ),
]

fixed_blocks = [
    CalendarBlock(
        block_id="class-1",
        title="수업",
        start=datetime(2026, 7, 1, 10, 0),
        end=datetime(2026, 7, 1, 12, 0),
        source="fixed",
        locked=True,
    ),
    CalendarBlock(
        block_id="part-time",
        title="알바",
        start=datetime(2026, 7, 1, 18, 0),
        end=datetime(2026, 7, 1, 22, 0),
        source="fixed",
        locked=True,
    ),
]

result = schedule_tasks(tasks, fixed_blocks, preferences, now)
pprint(result.to_dict(), sort_dicts=False)
