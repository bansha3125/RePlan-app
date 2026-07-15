from __future__ import annotations

from collections import Counter, defaultdict
from datetime import datetime, timedelta
from statistics import mean
from typing import Any, Iterable, Optional

from .models import CalendarBlock, ExecutionRecord, SchedulePreferences


def detect_repeated_postponement(
    records: Iterable[ExecutionRecord],
    threshold: int = 3,
) -> list[dict[str, Any]]:
    """작업별 누적 미루기 횟수가 기준 이상인 항목을 반환한다."""
    latest: dict[str, ExecutionRecord] = {}
    for record in records:
        previous = latest.get(record.task_id)
        if previous is None or record.scheduled_start > previous.scheduled_start:
            latest[record.task_id] = record

    return [
        {
            "task_id": record.task_id,
            "category": record.category,
            "postponed_count": record.postponed_count,
        }
        for record in latest.values()
        if record.postponed_count >= threshold
    ]


def average_actual_minutes_by_category(
    records: Iterable[ExecutionRecord],
) -> dict[str, float]:
    grouped: dict[str, list[int]] = defaultdict(list)
    for record in records:
        if record.actual_minutes is not None and record.actual_minutes > 0:
            grouped[record.category].append(record.actual_minutes)

    return {
        category: round(mean(values), 1)
        for category, values in grouped.items()
    }


def adjusted_estimate_minutes(
    category: str,
    original_estimate: int,
    records: Iterable[ExecutionRecord],
    minimum_samples: int = 2,
) -> int:
    """
    같은 카테고리의 실제/예상 시간 비율로 다음 예상 시간을 보정한다.
    지나친 보정을 막기 위해 0.5~2.0배로 제한한다.
    """
    ratios: list[float] = []
    for record in records:
        if (
            record.category == category
            and record.actual_minutes is not None
            and record.actual_minutes > 0
            and record.planned_minutes > 0
        ):
            ratios.append(record.actual_minutes / record.planned_minutes)

    if len(ratios) < minimum_samples:
        return original_estimate

    ratio = min(max(mean(ratios), 0.5), 2.0)
    return max(1, round(original_estimate * ratio))


def completion_rate(records: Iterable[ExecutionRecord]) -> float:
    items = list(records)
    if not items:
        return 0.0
    return round(sum(record.completed for record in items) / len(items) * 100, 1)


def idle_gap_minutes(
    blocks: Iterable[CalendarBlock],
    preferences: SchedulePreferences,
) -> int:
    """일정이 있는 날짜마다 활동 가능 시간 안의 총 공백 시간을 계산한다."""
    by_date: dict[datetime.date, list[CalendarBlock]] = defaultdict(list)
    for block in blocks:
        by_date[block.start.date()].append(block)

    total_gap = 0
    for target_date, day_blocks in by_date.items():
        day_start = datetime.combine(target_date, preferences.day_start)
        day_end = datetime.combine(target_date, preferences.day_end)
        clipped = sorted(
            (
                max(block.start, day_start),
                min(block.end, day_end),
            )
            for block in day_blocks
            if block.end > day_start and block.start < day_end
        )

        cursor = day_start
        for start, end in clipped:
            if start > cursor:
                total_gap += int((start - cursor).total_seconds() // 60)
            cursor = max(cursor, end)
        if cursor < day_end:
            total_gap += int((day_end - cursor).total_seconds() // 60)

    return total_gap


def missed_task_patterns(
    records: Iterable[ExecutionRecord],
) -> dict[str, Any]:
    missed = [record for record in records if not record.completed]
    weekday_counter = Counter(record.scheduled_start.strftime("%A") for record in missed)
    hour_counter = Counter(record.scheduled_start.hour for record in missed)
    category_counter = Counter(record.category for record in missed)

    return {
        "missed_count": len(missed),
        "most_missed_weekday": weekday_counter.most_common(1)[0][0]
        if weekday_counter
        else None,
        "most_missed_start_hour": hour_counter.most_common(1)[0][0]
        if hour_counter
        else None,
        "most_missed_category": category_counter.most_common(1)[0][0]
        if category_counter
        else None,
    }


def build_feedback_data(
    records: Iterable[ExecutionRecord],
    blocks: Iterable[CalendarBlock],
    preferences: Optional[SchedulePreferences] = None,
) -> dict[str, Any]:
    records = list(records)
    blocks = list(blocks)
    preferences = preferences or SchedulePreferences()

    return {
        "completion_rate_percent": completion_rate(records),
        "average_actual_minutes_by_category": average_actual_minutes_by_category(records),
        "repeated_postponement": detect_repeated_postponement(records),
        "idle_gap_minutes": idle_gap_minutes(blocks, preferences),
        "missed_patterns": missed_task_patterns(records),
    }
