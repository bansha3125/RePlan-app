from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, time
from typing import Any, Optional


@dataclass
class Task:
    """스케줄링 엔진이 처리하는 작업 데이터."""

    id: str
    title: str
    deadline: datetime
    estimated_minutes: int
    priority: int = 3
    difficulty: int = 3
    focus_required: int = 3
    postpone_count: int = 0
    prerequisite_ids: list[str] = field(default_factory=list)
    category: str = "기타"
    course_id: Optional[str] = None
    week_order: Optional[int] = None
    task_type: str = "general"
    lecture_start: Optional[datetime] = None
    splittable: bool = True
    min_block_minutes: int = 30
    max_block_minutes: int = 60
    completed_minutes: int = 0
    completed: bool = False

    def __post_init__(self) -> None:
        if not self.id.strip():
            raise ValueError("Task.id는 비어 있을 수 없습니다.")
        if not self.title.strip():
            raise ValueError("Task.title은 비어 있을 수 없습니다.")
        if self.estimated_minutes <= 0:
            raise ValueError("estimated_minutes는 1 이상이어야 합니다.")
        if not 1 <= self.priority <= 5:
            raise ValueError("priority는 1~5 사이여야 합니다.")
        if not 1 <= self.difficulty <= 5:
            raise ValueError("difficulty는 1~5 사이여야 합니다.")
        if not 1 <= self.focus_required <= 5:
            raise ValueError("focus_required는 1~5 사이여야 합니다.")
        if self.postpone_count < 0:
            raise ValueError("postpone_count는 0 이상이어야 합니다.")
        if self.completed_minutes < 0:
            raise ValueError("completed_minutes는 0 이상이어야 합니다.")
        if self.min_block_minutes <= 0:
            raise ValueError("min_block_minutes는 1 이상이어야 합니다.")
        if self.max_block_minutes < self.min_block_minutes:
            raise ValueError("max_block_minutes는 min_block_minutes 이상이어야 합니다.")

    @property
    def remaining_minutes(self) -> int:
        if self.completed:
            return 0
        return max(self.estimated_minutes - self.completed_minutes, 0)

    @property
    def effective_deadline(self) -> datetime:
        """복습 작업은 강의 시작 전에 끝나도록 강의 시간을 실질 마감으로 사용한다."""
        if self.lecture_start is not None:
            return min(self.deadline, self.lecture_start)
        return self.deadline


@dataclass
class CalendarBlock:
    """고정 일정, 수동 고정 일정, 긴급 일정, 자동 생성 일정."""

    block_id: str
    title: str
    start: datetime
    end: datetime
    source: str = "fixed"
    task_id: Optional[str] = None
    locked: bool = True
    completed: bool = False
    reason: Optional[str] = None

    def __post_init__(self) -> None:
        if self.end <= self.start:
            raise ValueError("CalendarBlock.end는 start보다 늦어야 합니다.")

    @property
    def duration_minutes(self) -> int:
        return int((self.end - self.start).total_seconds() // 60)


@dataclass
class SchedulePreferences:
    """사용자의 기본 활동 시간과 집중 시간대."""

    day_start: time = time(9, 0)
    day_end: time = time(22, 0)
    slot_minutes: int = 30
    focus_start: time = time(9, 0)
    focus_end: time = time(12, 0)
    max_daily_generated_minutes: int = 480

    def __post_init__(self) -> None:
        if self.day_end <= self.day_start:
            raise ValueError("day_end는 day_start보다 늦어야 합니다.")
        if self.slot_minutes <= 0:
            raise ValueError("slot_minutes는 1 이상이어야 합니다.")
        if self.max_daily_generated_minutes <= 0:
            raise ValueError("max_daily_generated_minutes는 1 이상이어야 합니다.")


@dataclass(frozen=True)
class TimeSlot:
    start: datetime
    end: datetime

    @property
    def duration_minutes(self) -> int:
        return int((self.end - self.start).total_seconds() // 60)


@dataclass
class ScoreBreakdown:
    urgency: float
    priority: float
    postponement: float
    prerequisite: float
    focus_and_difficulty: float

    @property
    def total(self) -> float:
        return (
            self.urgency
            + self.priority
            + self.postponement
            + self.prerequisite
            + self.focus_and_difficulty
        )


@dataclass
class ScheduleWarning:
    code: str
    message: str
    task_id: Optional[str] = None
    details: dict[str, Any] = field(default_factory=dict)


@dataclass
class ScheduleResult:
    blocks: list[CalendarBlock]
    preserved_blocks: list[CalendarBlock]
    warnings: list[ScheduleWarning]
    scores: dict[str, ScoreBreakdown]

    def to_dict(self) -> dict[str, Any]:
        return {
            "blocks": [_serialize(block) for block in self.blocks],
            "preserved_blocks": [_serialize(block) for block in self.preserved_blocks],
            "warnings": [_serialize(warning) for warning in self.warnings],
            "scores": {
                task_id: {
                    **asdict(score),
                    "total": score.total,
                }
                for task_id, score in self.scores.items()
            },
        }


@dataclass
class ExecutionRecord:
    """예상 시간 보정과 수행 패턴 분석에 사용하는 기록."""

    task_id: str
    category: str
    planned_minutes: int
    actual_minutes: Optional[int]
    scheduled_start: datetime
    completed: bool
    postponed_count: int = 0
    completed_at: Optional[datetime] = None


def _serialize(value: Any) -> Any:
    if isinstance(value, datetime):
        return value.isoformat(timespec="minutes")
    if isinstance(value, time):
        return value.isoformat(timespec="minutes")
    if hasattr(value, "__dataclass_fields__"):
        return {key: _serialize(item) for key, item in asdict(value).items()}
    if isinstance(value, list):
        return [_serialize(item) for item in value]
    if isinstance(value, dict):
        return {key: _serialize(item) for key, item in value.items()}
    return value
