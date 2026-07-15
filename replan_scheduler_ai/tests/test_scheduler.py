from __future__ import annotations

import unittest
from datetime import datetime, time

from ai_scheduler.models import CalendarBlock, SchedulePreferences, Task
from ai_scheduler.scheduler import (
    calculate_score,
    compute_free_slots,
    replan_after_postpone,
    replan_with_urgent_event,
    schedule_tasks,
)


NOW = datetime(2026, 7, 1, 8, 0)
PREF = SchedulePreferences(
    day_start=time(9, 0),
    day_end=time(18, 0),
    slot_minutes=30,
    focus_start=time(9, 0),
    focus_end=time(12, 0),
    max_daily_generated_minutes=480,
)


class SchedulerTests(unittest.TestCase):
    def test_free_slots_exclude_fixed_schedule(self) -> None:
        fixed = CalendarBlock(
            block_id="class",
            title="수업",
            start=datetime(2026, 7, 1, 10, 0),
            end=datetime(2026, 7, 1, 12, 0),
            source="fixed",
            locked=True,
        )
        slots = compute_free_slots(
            datetime(2026, 7, 1, 9, 0),
            datetime(2026, 7, 1, 18, 0),
            [fixed],
            PREF,
        )
        self.assertEqual(
            [(slot.start.hour, slot.end.hour) for slot in slots],
            [(9, 10), (12, 18)],
        )

    def test_generated_block_does_not_overlap_fixed_schedule(self) -> None:
        task = Task(
            id="t1",
            title="과제",
            deadline=datetime(2026, 7, 1, 18, 0),
            estimated_minutes=120,
            priority=5,
        )
        fixed = CalendarBlock(
            block_id="class",
            title="수업",
            start=datetime(2026, 7, 1, 9, 0),
            end=datetime(2026, 7, 1, 12, 0),
            source="fixed",
            locked=True,
        )
        result = schedule_tasks([task], [fixed], PREF, NOW)
        self.assertTrue(result.blocks)
        for block in result.blocks:
            self.assertFalse(block.start < fixed.end and fixed.start < block.end)

    def test_long_task_is_split(self) -> None:
        task = Task(
            id="long",
            title="긴 보고서",
            deadline=datetime(2026, 7, 2, 18, 0),
            estimated_minutes=240,
            max_block_minutes=90,
            min_block_minutes=30,
        )
        result = schedule_tasks([task], [], PREF, NOW)
        blocks = [block for block in result.blocks if block.task_id == "long"]
        self.assertGreater(len(blocks), 1)
        self.assertEqual(sum(block.duration_minutes for block in blocks), 240)
        self.assertTrue(all(block.duration_minutes <= 90 for block in blocks))

    def test_prerequisite_is_scheduled_first(self) -> None:
        research = Task(
            id="research",
            title="자료 조사",
            deadline=datetime(2026, 7, 2, 18, 0),
            estimated_minutes=60,
            priority=3,
        )
        slides = Task(
            id="slides",
            title="PPT 제작",
            deadline=datetime(2026, 7, 2, 18, 0),
            estimated_minutes=60,
            priority=5,
            prerequisite_ids=["research"],
        )
        result = schedule_tasks([slides, research], [], PREF, NOW)
        research_end = max(
            block.end for block in result.blocks if block.task_id == "research"
        )
        slides_start = min(
            block.start for block in result.blocks if block.task_id == "slides"
        )
        self.assertLessEqual(research_end, slides_start)

    def test_week_order_is_applied(self) -> None:
        week_13 = Task(
            id="w13",
            title="13주차 복습",
            deadline=datetime(2026, 7, 3, 18, 0),
            estimated_minutes=60,
            course_id="english",
            week_order=13,
        )
        week_14 = Task(
            id="w14",
            title="14주차 예습",
            deadline=datetime(2026, 7, 3, 18, 0),
            estimated_minutes=60,
            priority=5,
            course_id="english",
            week_order=14,
        )
        result = schedule_tasks([week_14, week_13], [], PREF, NOW)
        end_13 = max(block.end for block in result.blocks if block.task_id == "w13")
        start_14 = min(block.start for block in result.blocks if block.task_id == "w14")
        self.assertLessEqual(end_13, start_14)

    def test_review_finishes_before_lecture(self) -> None:
        lecture_time = datetime(2026, 7, 2, 14, 0)
        review = Task(
            id="review",
            title="영어회화 복습",
            deadline=datetime(2026, 7, 5, 23, 0),
            estimated_minutes=90,
            lecture_start=lecture_time,
            task_type="review",
        )
        result = schedule_tasks([review], [], PREF, NOW)
        self.assertTrue(result.blocks)
        self.assertLessEqual(max(block.end for block in result.blocks), lecture_time)

    def test_manual_locked_block_is_preserved(self) -> None:
        task = Task(
            id="task",
            title="공부",
            deadline=datetime(2026, 7, 2, 18, 0),
            estimated_minutes=60,
        )
        manual = CalendarBlock(
            block_id="manual-1",
            title="사용자가 고정한 공부",
            start=datetime(2026, 7, 1, 13, 0),
            end=datetime(2026, 7, 1, 14, 0),
            source="generated",
            task_id="old",
            locked=True,
        )
        old_unlocked = CalendarBlock(
            block_id="old-auto",
            title="이전 자동 일정",
            start=datetime(2026, 7, 1, 14, 0),
            end=datetime(2026, 7, 1, 15, 0),
            source="generated",
            task_id="old2",
            locked=False,
        )
        result = schedule_tasks([task], [manual, old_unlocked], PREF, NOW)
        preserved_ids = {block.block_id for block in result.preserved_blocks}
        self.assertIn("manual-1", preserved_ids)
        self.assertNotIn("old-auto", preserved_ids)

    def test_urgent_event_triggers_replan_without_overlap(self) -> None:
        task = Task(
            id="task",
            title="개발",
            deadline=datetime(2026, 7, 1, 18, 0),
            estimated_minutes=120,
            focus_required=5,
        )
        urgent = CalendarBlock(
            block_id="urgent",
            title="긴급 회의",
            start=datetime(2026, 7, 1, 9, 0),
            end=datetime(2026, 7, 1, 11, 0),
            source="urgent",
            locked=True,
        )
        result = replan_with_urgent_event([task], [], urgent, PREF, NOW)
        self.assertIn("urgent", {b.block_id for b in result.preserved_blocks})
        for block in result.blocks:
            self.assertFalse(block.start < urgent.end and urgent.start < block.end)

    def test_unschedulable_task_returns_warning(self) -> None:
        task = Task(
            id="impossible",
            title="불가능한 작업",
            deadline=datetime(2026, 7, 1, 10, 0),
            estimated_minutes=180,
        )
        result = schedule_tasks([task], [], PREF, NOW)
        self.assertFalse(result.blocks)
        self.assertIn(
            "UNSCHEDULABLE_TASK",
            {warning.code for warning in result.warnings},
        )

    def test_postponement_increases_score(self) -> None:
        base = Task(
            id="task",
            title="과제",
            deadline=datetime(2026, 7, 5, 18, 0),
            estimated_minutes=60,
            postpone_count=0,
        )
        postponed = Task(
            id="task",
            title="과제",
            deadline=datetime(2026, 7, 5, 18, 0),
            estimated_minutes=60,
            postpone_count=2,
        )
        self.assertGreater(
            calculate_score(postponed, NOW).total,
            calculate_score(base, NOW).total,
        )

    def test_replan_after_postpone_runs(self) -> None:
        tasks = [
            Task(
                id="a",
                title="A",
                deadline=datetime(2026, 7, 2, 18, 0),
                estimated_minutes=60,
            )
        ]
        result = replan_after_postpone("a", tasks, [], PREF, NOW)
        self.assertEqual(result.scores["a"].postponement, 10.0)


if __name__ == "__main__":
    unittest.main()
