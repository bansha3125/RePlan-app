from __future__ import annotations

import unittest
from datetime import datetime

from ai_scheduler.analytics import (
    adjusted_estimate_minutes,
    build_feedback_data,
    completion_rate,
    detect_repeated_postponement,
)
from ai_scheduler.models import ExecutionRecord


class AnalyticsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.records = [
            ExecutionRecord(
                task_id="a",
                category="공부",
                planned_minutes=60,
                actual_minutes=90,
                scheduled_start=datetime(2026, 7, 1, 9, 0),
                completed=True,
                postponed_count=3,
            ),
            ExecutionRecord(
                task_id="b",
                category="공부",
                planned_minutes=60,
                actual_minutes=90,
                scheduled_start=datetime(2026, 7, 2, 9, 0),
                completed=False,
                postponed_count=1,
            ),
        ]

    def test_completion_rate(self) -> None:
        self.assertEqual(completion_rate(self.records), 50.0)

    def test_adjusted_estimate(self) -> None:
        self.assertEqual(
            adjusted_estimate_minutes("공부", 60, self.records),
            90,
        )

    def test_repeated_postponement(self) -> None:
        repeated = detect_repeated_postponement(self.records)
        self.assertEqual(repeated[0]["task_id"], "a")

    def test_feedback_data(self) -> None:
        data = build_feedback_data(self.records, [])
        self.assertIn("completion_rate_percent", data)
        self.assertIn("missed_patterns", data)


if __name__ == "__main__":
    unittest.main()
