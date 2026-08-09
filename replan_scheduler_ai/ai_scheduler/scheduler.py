from __future__ import annotations

import math
from collections import defaultdict
from dataclasses import replace
from datetime import date, datetime, time, timedelta
from typing import Iterable, Optional

from .models import (
    CalendarBlock,
    SchedulePreferences,
    ScheduleResult,
    ScheduleWarning,
    ScoreBreakdown,
    Task,
    TimeSlot,
)


PRESERVED_SOURCES = {"fixed", "manual", "urgent"}


def ceil_datetime(value: datetime, unit_minutes: int) -> datetime:
    """datetime을 일정 배치 단위로 올림한다."""
    value = value.replace(second=0, microsecond=0)
    minutes_from_midnight = value.hour * 60 + value.minute
    rounded = math.ceil(minutes_from_midnight / unit_minutes) * unit_minutes
    day_offset, minute_of_day = divmod(rounded, 24 * 60)
    return datetime.combine(
        value.date() + timedelta(days=day_offset),
        time(minute_of_day // 60, minute_of_day % 60),
    )


def round_up_minutes(minutes: int, unit_minutes: int) -> int:
    return math.ceil(minutes / unit_minutes) * unit_minutes


def calculate_score(
    task: Task,
    now: datetime,
    dependent_count: int = 0,
) -> ScoreBreakdown:
    """
    최종 점수 = 마감 임박 + 사용자 중요도 + 미루기 + 선행 작업 + 집중·난이도.
    """
    hours_left = (task.effective_deadline - now).total_seconds() / 3600

    if hours_left <= 24:
        urgency = 100.0
    elif hours_left <= 72:
        urgency = 80.0 + ((72 - hours_left) / 48) * 20
    elif hours_left <= 168:
        urgency = 50.0 + ((168 - hours_left) / 96) * 30
    elif hours_left <= 336:
        urgency = 20.0 + ((336 - hours_left) / 168) * 30
    else:
        urgency = max(5.0, 20.0 - ((hours_left - 336) / 336) * 15)

    priority_score = task.priority * 10.0
    postponement_score = min(task.postpone_count * 10.0, 40.0)
    prerequisite_score = min(dependent_count * 8.0, 32.0)
    focus_and_difficulty = (
        (task.focus_required - 1) * 3.0
        + (task.difficulty - 1) * 2.0
    )

    return ScoreBreakdown(
        urgency=round(urgency, 2),
        priority=priority_score,
        postponement=postponement_score,
        prerequisite=prerequisite_score,
        focus_and_difficulty=focus_and_difficulty,
    )


def _merge_intervals(
    intervals: Iterable[tuple[datetime, datetime]],
) -> list[tuple[datetime, datetime]]:
    ordered = sorted(intervals, key=lambda item: item[0])
    merged: list[tuple[datetime, datetime]] = []

    for start, end in ordered:
        if not merged or start > merged[-1][1]:
            merged.append((start, end))
        else:
            previous_start, previous_end = merged[-1]
            merged[-1] = (previous_start, max(previous_end, end))

    return merged


def compute_free_slots(
    range_start: datetime,
    range_end: datetime,
    busy_blocks: Iterable[CalendarBlock],
    preferences: SchedulePreferences,
) -> list[TimeSlot]:
    """고정·수동·긴급·이미 배치된 구간을 제외한 빈 시간을 계산한다."""
    if range_end <= range_start:
        return []

    busy = _merge_intervals(
        (block.start, block.end)
        for block in busy_blocks
        if block.end > range_start and block.start < range_end
    )

    slots: list[TimeSlot] = []
    current_day = range_start.date()
    last_day = range_end.date()

    while current_day <= last_day:
        day_start = datetime.combine(current_day, preferences.day_start)
        day_end = datetime.combine(current_day, preferences.day_end)
        window_start = max(day_start, range_start)
        window_end = min(day_end, range_end)

        if window_end > window_start:
            cursor = window_start
            for busy_start, busy_end in busy:
                if busy_end <= window_start or busy_start >= window_end:
                    continue

                clipped_start = max(busy_start, window_start)
                clipped_end = min(busy_end, window_end)

                if clipped_start > cursor:
                    slots.append(TimeSlot(cursor, clipped_start))
                cursor = max(cursor, clipped_end)

            if cursor < window_end:
                slots.append(TimeSlot(cursor, window_end))

        current_day += timedelta(days=1)

    return slots


def _build_dependencies(tasks: list[Task]) -> dict[str, set[str]]:
    task_ids = {task.id for task in tasks}
    dependencies = {
        task.id: {item for item in task.prerequisite_ids if item in task_ids}
        for task in tasks
    }

    # 같은 강좌의 주차 순서가 있으면 직전 주차를 자동 선행 작업으로 연결한다.
    by_course: dict[str, list[Task]] = defaultdict(list)
    for task in tasks:
        if task.course_id and task.week_order is not None:
            by_course[task.course_id].append(task)

    for course_tasks in by_course.values():
        ordered = sorted(
            course_tasks,
            key=lambda task: (task.week_order or 0, task.effective_deadline),
        )
        for previous, current in zip(ordered, ordered[1:]):
            dependencies[current.id].add(previous.id)

    return dependencies


def _find_cycle_nodes(dependencies: dict[str, set[str]]) -> set[str]:
    visiting: set[str] = set()
    visited: set[str] = set()
    cycle_nodes: set[str] = set()

    def visit(node: str, path: list[str]) -> None:
        if node in visiting:
            if node in path:
                cycle_nodes.update(path[path.index(node):])
            return
        if node in visited:
            return

        visiting.add(node)
        path.append(node)
        for dependency in dependencies.get(node, set()):
            visit(dependency, path)
        path.pop()
        visiting.remove(node)
        visited.add(node)

    for node in dependencies:
        visit(node, [])

    return cycle_nodes


def _dependent_counts(dependencies: dict[str, set[str]]) -> dict[str, int]:
    counts: dict[str, int] = defaultdict(int)
    for dependency_set in dependencies.values():
        for dependency in dependency_set:
            counts[dependency] += 1
    return counts


def _split_task(task: Task, preferences: SchedulePreferences) -> list[int]:
    remaining = round_up_minutes(task.remaining_minutes, preferences.slot_minutes)
    if remaining <= 0:
        return []
    if not task.splittable:
        return [remaining]

    maximum = max(
        preferences.slot_minutes,
        round_up_minutes(task.max_block_minutes, preferences.slot_minutes),
    )
    minimum = max(
        preferences.slot_minutes,
        round_up_minutes(task.min_block_minutes, preferences.slot_minutes),
    )

    chunks: list[int] = []
    while remaining > 0:
        chunk = min(maximum, remaining)
        leftover = remaining - chunk

        if 0 < leftover < minimum:
            chunk -= minimum - leftover
            leftover = minimum

        if chunk <= 0:
            chunk = remaining
            leftover = 0

        chunks.append(chunk)
        remaining = leftover

    return chunks


def _minutes_generated_on_date(
    blocks: Iterable[CalendarBlock],
    target_date: date,
) -> int:
    return sum(
        block.duration_minutes
        for block in blocks
        if block.source == "generated" and block.start.date() == target_date
    )


def _is_in_focus_window(
    candidate_start: datetime,
    candidate_end: datetime,
    preferences: SchedulePreferences,
) -> bool:
    focus_start = datetime.combine(candidate_start.date(), preferences.focus_start)
    focus_end = datetime.combine(candidate_start.date(), preferences.focus_end)
    midpoint = candidate_start + (candidate_end - candidate_start) / 2
    return focus_start <= midpoint < focus_end


def _candidate_quality(
    task: Task,
    candidate_start: datetime,
    candidate_end: datetime,
    search_start: datetime,
    preferences: SchedulePreferences,
) -> float:
    in_focus = _is_in_focus_window(
        candidate_start,
        candidate_end,
        preferences,
    )

    # 집중도가 높은 작업만 집중 시간대를 강하게 선호
    if task.focus_required >= 4:
        focus_match = 15.0 if in_focus else 0.0

    elif task.focus_required <= 2:
        # 집중도가 낮다고 해서 오전 빈칸을 피하지 않도록 완화
        focus_match = 2.0 if not in_focus else 0.0

    else:
        focus_match = 6.0 if in_focus else 3.0

    difficulty_match = (
        5.0
        if task.difficulty >= 4 and in_focus
        else 0.0
    )

    hours_from_start = (
        candidate_start - search_start
    ).total_seconds() / 3600

    # 앞쪽 빈 시간을 훨씬 적극적으로 선호
    early_bonus = max(
        0.0,
        30.0 - hours_from_start * 2.0
    )

    return (
        focus_match
        + difficulty_match
        + early_bonus
    )


def _find_best_candidate(
    task: Task,
    duration_minutes: int,
    earliest_start: datetime,
    deadline: datetime,
    busy_blocks: list[CalendarBlock],
    generated_blocks: list[CalendarBlock],
    preferences: SchedulePreferences,
) -> Optional[tuple[datetime, datetime]]:
    free_slots = compute_free_slots(
        earliest_start,
        deadline,
        [*busy_blocks, *generated_blocks],
        preferences,
    )

    best: Optional[tuple[float, datetime, datetime]] = None
    step = timedelta(minutes=preferences.slot_minutes)

    for slot in free_slots:
        candidate_start = ceil_datetime(slot.start, preferences.slot_minutes)
        while candidate_start + timedelta(minutes=duration_minutes) <= slot.end:
            candidate_end = candidate_start + timedelta(minutes=duration_minutes)

            generated_today = _minutes_generated_on_date(
                generated_blocks,
                candidate_start.date(),
            )
            if (
                generated_today + duration_minutes
                <= preferences.max_daily_generated_minutes
            ):
                quality = _candidate_quality(
                    task,
                    candidate_start,
                    candidate_end,
                    earliest_start,
                    preferences,
                )
                candidate = (quality, candidate_start, candidate_end)

                if best is None:
                    best = candidate
                else:
                    best_quality, best_start, _ = best
                    if quality > best_quality or (
                        math.isclose(quality, best_quality)
                        and candidate_start < best_start
                    ):
                        best = candidate

            candidate_start += step

    if best is None:
        return None
    return best[1], best[2]


def _default_reason(task: Task, score: ScoreBreakdown, block_index: int, block_count: int) -> str:
    parts = [
        f"마감 임박도 {score.urgency:.0f}점",
        f"중요도 {task.priority}/5",
    ]
    if task.postpone_count:
        parts.append(f"미루기 {task.postpone_count}회")
    if task.lecture_start:
        parts.append("강의 시작 전 복습 필요")
    if block_count > 1:
        parts.append(f"긴 작업 분할 {block_index}/{block_count}")
    return ", ".join(parts)


def schedule_tasks(
    tasks: list[Task],
    existing_blocks: list[CalendarBlock],
    preferences: Optional[SchedulePreferences] = None,
    now: Optional[datetime] = None,
) -> ScheduleResult:
    """
    핵심 스케줄링 엔진.

    - 고정/수동 고정/긴급 일정 유지
    - 자동 생성된 미고정 일정은 버리고 재배치
    - 선행 작업과 주차 순서 보장
    - 강의 전 복습 배치
    - 긴 작업 분할
    - 불가능 작업 경고
    """
    preferences = preferences or SchedulePreferences()
    now = now or datetime.now()

    preserved_blocks = [
        block
        for block in existing_blocks
        if block.locked or block.source in PRESERVED_SOURCES
    ]

    active_tasks = [
        task
        for task in tasks
        if not task.completed and task.remaining_minutes > 0
    ]
    task_by_id = {task.id: task for task in active_tasks}
    dependencies = _build_dependencies(active_tasks)
    dependent_counts = _dependent_counts(dependencies)

    scores = {
        task.id: calculate_score(task, now, dependent_counts.get(task.id, 0))
        for task in active_tasks
    }

    warnings: list[ScheduleWarning] = []
    cycle_nodes = _find_cycle_nodes(dependencies)
    for task_id in sorted(cycle_nodes):
        warnings.append(
            ScheduleWarning(
                code="DEPENDENCY_CYCLE",
                task_id=task_id,
                message="선행 작업 관계가 순환하여 일정을 배치할 수 없습니다.",
            )
        )

    pending = {
        task_id: task
        for task_id, task in task_by_id.items()
        if task_id not in cycle_nodes
    }
    failed_ids: set[str] = set(cycle_nodes)
    scheduled_end: dict[str, datetime] = {}
    generated_blocks: list[CalendarBlock] = []

    completed_task_ids = {task.id for task in tasks if task.completed}

    while pending:
        ready: list[Task] = []

        for task_id, task in pending.items():
            deps = dependencies.get(task_id, set())
            if any(dep in failed_ids for dep in deps):
                continue
            if all(dep in completed_task_ids or dep in scheduled_end for dep in deps):
                ready.append(task)

        if not ready:
            for task_id, task in list(pending.items()):
                deps = dependencies.get(task_id, set())
                missing = sorted(
                    dep
                    for dep in deps
                    if dep not in completed_task_ids and dep not in scheduled_end
                )
                warnings.append(
                    ScheduleWarning(
                        code="PREREQUISITE_NOT_SCHEDULED",
                        task_id=task_id,
                        message="선행 작업이 완료되거나 배치되지 않아 일정을 만들 수 없습니다.",
                        details={"missing_prerequisite_ids": missing},
                    )
                )
                failed_ids.add(task_id)
                pending.pop(task_id)
            break

        ready.sort(
            key=lambda task: (
                -scores[task.id].total,
                task.effective_deadline,
                task.id,
            )
        )
        task = ready[0]
        pending.pop(task.id)

        deadline = task.effective_deadline
        deps = dependencies.get(task.id, set())
        earliest_start = now
        prerequisite_ends = [
            scheduled_end[dep]
            for dep in deps
            if dep in scheduled_end
        ]
        if prerequisite_ends:
            earliest_start = max(earliest_start, max(prerequisite_ends))

        if deadline <= earliest_start:
            failed_ids.add(task.id)
            warnings.append(
                ScheduleWarning(
                    code="DEADLINE_ALREADY_PASSED",
                    task_id=task.id,
                    message="마감 또는 강의 시작 시간이 이미 지나 배치할 수 없습니다.",
                    details={"effective_deadline": deadline.isoformat(timespec="minutes")},
                )
            )
            continue

        chunks = _split_task(task, preferences)
        task_blocks: list[CalendarBlock] = []
        chunk_earliest = earliest_start

        for index, duration in enumerate(chunks, start=1):
            candidate = _find_best_candidate(
                task=task,
                duration_minutes=duration,
                earliest_start=chunk_earliest,
                deadline=deadline,
                busy_blocks=preserved_blocks,
                generated_blocks=[*generated_blocks, *task_blocks],
                preferences=preferences,
            )

            if candidate is None:
                task_blocks = []
                break

            start, end = candidate
            task_blocks.append(
                CalendarBlock(
                    block_id=f"generated:{task.id}:{index}",
                    title=task.title,
                    start=start,
                    end=end,
                    source="generated",
                    task_id=task.id,
                    locked=False,
                    reason=_default_reason(
                        task,
                        scores[task.id],
                        index,
                        len(chunks),
                    ),
                )
            )
            chunk_earliest = end

        if not task_blocks:
            failed_ids.add(task.id)
            available_minutes = sum(
                slot.duration_minutes
                for slot in compute_free_slots(
                    earliest_start,
                    deadline,
                    [*preserved_blocks, *generated_blocks],
                    preferences,
                )
            )
            warnings.append(
                ScheduleWarning(
                    code="UNSCHEDULABLE_TASK",
                    task_id=task.id,
                    message="마감 전 빈 시간이 부족하여 작업을 배치할 수 없습니다.",
                    details={
                        "required_minutes": round_up_minutes(
                            task.remaining_minutes,
                            preferences.slot_minutes,
                        ),
                        "available_minutes": available_minutes,
                        "effective_deadline": deadline.isoformat(timespec="minutes"),
                    },
                )
            )
            continue

        generated_blocks.extend(task_blocks)
        scheduled_end[task.id] = max(block.end for block in task_blocks)

    generated_blocks.sort(key=lambda block: (block.start, block.end, block.block_id))
    preserved_blocks.sort(key=lambda block: (block.start, block.end, block.block_id))

    return ScheduleResult(
        blocks=generated_blocks,
        preserved_blocks=preserved_blocks,
        warnings=warnings,
        scores=scores,
    )


def replan_with_urgent_event(
    tasks: list[Task],
    existing_blocks: list[CalendarBlock],
    urgent_event: CalendarBlock,
    preferences: Optional[SchedulePreferences] = None,
    now: Optional[datetime] = None,
) -> ScheduleResult:
    """긴급 일정을 고정한 뒤 기존 자동 일정을 전부 다시 계산한다."""
    urgent = replace(urgent_event, source="urgent", locked=True)
    return schedule_tasks(
        tasks=tasks,
        existing_blocks=[*existing_blocks, urgent],
        preferences=preferences,
        now=now,
    )


def replan_after_postpone(
    task_id: str,
    tasks: list[Task],
    existing_blocks: list[CalendarBlock],
    preferences: Optional[SchedulePreferences] = None,
    now: Optional[datetime] = None,
) -> ScheduleResult:
    """미룬 횟수를 1 증가시킨 복사본으로 재배치한다."""
    updated_tasks = [
        replace(task, postpone_count=task.postpone_count + 1)
        if task.id == task_id
        else task
        for task in tasks
    ]
    return schedule_tasks(
        tasks=updated_tasks,
        existing_blocks=existing_blocks,
        preferences=preferences,
        now=now,
    )
