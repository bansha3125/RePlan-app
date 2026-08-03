from datetime import date, datetime
from typing import Any, Dict, List, Optional

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from ai_scheduler.integration import (
    replan_api_from_payload,
    schedule_api_from_payload,
)


app = FastAPI(
    title="AI Scheduler API",
    version="1.0.0",
)


# =========================================================
# 요청 모델
# =========================================================

class TaskRequest(BaseModel):
    taskId: int
    title: str
    estimatedMinutes: int
    deadline: datetime
    priority: int

    difficulty: Optional[int] = None
    focusRequired: Optional[int] = None

    postponeCount: int = 0
    completedMinutes: int = 0
    remainingMinutes: Optional[int] = None
    completed: bool = False

    prerequisiteTaskIds: List[int] = Field(
        default_factory=list
    )


class FixedScheduleRequest(BaseModel):
    fixedScheduleId: int
    title: str
    startTime: datetime
    endTime: datetime


class GenerateScheduleRequest(BaseModel):
    requestId: str
    userId: int

    weekStartDate: date
    weekEndDate: date

    timezone: str = "Asia/Seoul"

    tasks: List[TaskRequest] = Field(
        default_factory=list
    )

    fixedSchedules: List[FixedScheduleRequest] = Field(
        default_factory=list
    )

    existingSchedules: List[Dict[str, Any]] = Field(
        default_factory=list
    )

class ReplanScheduleRequest(
        GenerateScheduleRequest
    ):
        completedTaskIds: List[int] = Field(
            default_factory=list
        )

        postponedTaskIds: List[int] = Field(
            default_factory=list
        )

        replanFromTime: Optional[datetime] = None

# =========================================================
# 백엔드 요청 → 기존 AI 내부 입력 변환
# =========================================================

def _convert_existing_schedule(
    item: Dict[str, Any],
) -> Dict[str, Any]:
    """
    백엔드의 camelCase 기존 일정을
    AI 내부 snake_case 일정으로 변환한다.
    """

    block_id = item.get(
        "blockId",
        item.get("block_id"),
    )

    start_time = item.get(
        "startTime",
        item.get("start"),
    )

    end_time = item.get(
        "endTime",
        item.get("end"),
    )

    task_id = item.get(
        "taskId",
        item.get("task_id"),
    )

    if block_id is None:
        raise ValueError(
            "existingSchedules에 blockId가 없습니다."
        )

    if start_time is None:
        raise ValueError(
            "existingSchedules에 startTime이 없습니다."
        )

    if end_time is None:
        raise ValueError(
            "existingSchedules에 endTime이 없습니다."
        )

    return {
        "block_id": str(block_id),
        "title": str(item.get("title", "기존 일정")),
        "start": str(start_time),
        "end": str(end_time),
        "source": str(
            item.get("source", "generated")
        ).lower(),
        "task_id": (
            str(task_id)
            if task_id is not None
            else None
        ),
        "locked": bool(
            item.get("locked", False)
        ),
        "completed": bool(
            item.get("completed", False)
        ),
        "reason": item.get("reason"),
    }


def _request_to_internal_payload(
    request: GenerateScheduleRequest,
) -> Dict[str, Any]:
    """
    FastAPI 요청 모델을 기존 schedule_from_payload가
    이해할 수 있는 내부 형식으로 변환한다.
    """

    tasks = []

    for task in request.tasks:
        tasks.append({
            "id": str(task.taskId),
            "title": task.title,
            "deadline": task.deadline.isoformat(),
            "estimated_minutes": task.estimatedMinutes,
            "priority": task.priority,
            "difficulty": (
                task.difficulty
                if task.difficulty is not None
                else 3
            ),
            "focus_required": (
                task.focusRequired
                if task.focusRequired is not None
                else 3
            ),
            "postpone_count": 0,
            "prerequisite_ids": [],
            "category": "기타",
            "splittable": True,
            "min_block_minutes": 30,
            "max_block_minutes": 90,
            "completed_minutes": 0,
            "completed": False,
        })

    existing_blocks = []

    # 수업·알바·병원 예약 등의 고정 일정
    for fixed in request.fixedSchedules:
        existing_blocks.append({
            "block_id": (
                f"fixed:{fixed.fixedScheduleId}"
            ),
            "title": fixed.title,
            "start": fixed.startTime.isoformat(),
            "end": fixed.endTime.isoformat(),
            "source": "fixed",
            "task_id": None,
            "locked": True,
            "completed": False,
            "reason": None,
        })

    # 기존 AI 생성 일정
    for existing in request.existingSchedules:
        existing_blocks.append(
            _convert_existing_schedule(existing)
        )

    return {
        "tasks": tasks,
        "existing_blocks": existing_blocks,

        # integration.py에 기본값이 있지만
        # 현재 서비스 규칙을 명시적으로 전달
        "preferences": {
            "day_start": "09:00",
            "day_end": "22:00",
            "slot_minutes": 30,
            "focus_start": "09:00",
            "focus_end": "12:00",
            "max_daily_generated_minutes": 480,
        },
    }

def _parse_existing_datetime(
    value: Any,
) -> datetime:
    if isinstance(value, datetime):
        return value

    return datetime.fromisoformat(str(value))


def _request_to_replan_internal_payload(
    request: ReplanScheduleRequest,
) -> Dict[str, Any]:
    """
    재배치 요청을 기존 스케줄러가 이해하는
    snake_case 구조로 변환한다.
    """

    completed_ids = {
        str(task_id)
        for task_id in request.completedTaskIds
    }

    postponed_ids = {
        str(task_id)
        for task_id in request.postponedTaskIds
    }

    # locked 일정이 이미 차지하는 작업 시간 계산
    locked_minutes_by_task: Dict[str, int] = {}

    for item in request.existingSchedules:
        if not bool(item.get("locked", False)):
            continue

        if bool(item.get("completed", False)):
            continue

        task_id = item.get(
            "taskId",
            item.get("task_id"),
        )

        if task_id is None:
            continue

        start_value = item.get(
            "startTime",
            item.get("start"),
        )

        end_value = item.get(
            "endTime",
            item.get("end"),
        )

        if start_value is None or end_value is None:
            continue

        start_time = _parse_existing_datetime(
            start_value
        )

        end_time = _parse_existing_datetime(
            end_value
        )

        duration_minutes = max(
            0,
            int(
                (
                    end_time - start_time
                ).total_seconds()
                // 60
            ),
        )

        task_id_string = str(task_id)

        locked_minutes_by_task[
            task_id_string
        ] = (
            locked_minutes_by_task.get(
                task_id_string,
                0,
            )
            + duration_minutes
        )

    tasks = []

    for task in request.tasks:
        task_id = str(task.taskId)

        # 완료 작업은 다시 배치하지 않음
        if (
            task_id in completed_ids
            or task.completed
        ):
            continue

        if task.remainingMinutes is not None:
            remaining_minutes = (
                task.remainingMinutes
            )
        else:
            remaining_minutes = max(
                0,
                task.estimatedMinutes
                - task.completedMinutes,
            )

        # 이미 locked된 시간만큼 제외
        remaining_minutes = max(
            0,
            remaining_minutes
            - locked_minutes_by_task.get(
                task_id,
                0,
            ),
        )

        if remaining_minutes <= 0:
            continue

        postpone_count = task.postponeCount

        if task_id in postponed_ids:
            postpone_count += 1

        tasks.append({
            "id": task_id,
            "title": task.title,
            "deadline": task.deadline.isoformat(),
            "estimated_minutes": remaining_minutes,
            "priority": task.priority,
            "difficulty": (
                task.difficulty
                if task.difficulty is not None
                else 3
            ),
            "focus_required": (
                task.focusRequired
                if task.focusRequired is not None
                else 3
            ),
            "postpone_count": postpone_count,
            "prerequisite_ids": [
                str(value)
                for value
                in task.prerequisiteTaskIds
            ],
            "category": "기타",
            "splittable": True,
            "min_block_minutes": 30,
            "max_block_minutes": 90,
            "completed_minutes": 0,
            "completed": False,
        })

    existing_blocks = []

    # 긴급 약속·수업 등 고정 일정
    for fixed in request.fixedSchedules:
        existing_blocks.append({
            "block_id": (
                f"fixed:{fixed.fixedScheduleId}"
            ),
            "title": fixed.title,
            "start": fixed.startTime.isoformat(),
            "end": fixed.endTime.isoformat(),
            "source": "fixed",
            "task_id": None,
            "locked": True,
            "completed": False,
            "reason": None,
        })

    # 기존 일정 중 locked 일정만 유지
    for item in request.existingSchedules:
        task_id = item.get(
            "taskId",
            item.get("task_id"),
        )

        if bool(item.get("completed", False)):
            continue

        if (
            task_id is not None
            and str(task_id) in completed_ids
        ):
            continue

        if not bool(item.get("locked", False)):
            continue

        existing_blocks.append(
            _convert_existing_schedule(item)
        )

    if request.replanFromTime is not None:
        replan_from_time = (
            request.replanFromTime
        )
    else:
        replan_from_time = datetime.now().replace(
            microsecond=0
        )

    return {
        "tasks": tasks,
        "existing_blocks": existing_blocks,
        "preferences": {
            "day_start": "09:00",
            "day_end": "22:00",
            "slot_minutes": 30,
            "focus_start": "09:00",
            "focus_end": "12:00",
            "max_daily_generated_minutes": 480,
        },
        "now": replan_from_time.isoformat(),
    }

# =========================================================
# API
# =========================================================

@app.get("/")
@app.get("/health")
def health_check() -> Dict[str, Any]:
    return {
        "success": True,
        "status": "UP",
        "service": "replan-ai",
        "message": "AI Scheduler API is running",
    }


@app.post("/schedules/generate")
@app.post("/ai/schedules/generate")
def generate_schedule(
    request: GenerateScheduleRequest,
) -> Any:
    """
    최초 자동 일정을 생성한다.
    """

    try:
        internal_payload = (
            _request_to_internal_payload(request)
        )

        result = schedule_api_from_payload(
            internal_payload
        )

        # 요청과 결과를 연결할 수 있도록 requestId 포함
        result["requestId"] = request.requestId
        result["userId"] = request.userId
        result["weekStartDate"] = (
            request.weekStartDate.isoformat()
        )
        result["weekEndDate"] = (
            request.weekEndDate.isoformat()
        )
        result["timezone"] = request.timezone

        return result

    except (KeyError, TypeError, ValueError) as error:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "requestId": request.requestId,
                "message": (
                    "일정 생성 요청값이 "
                    "올바르지 않습니다."
                ),
                "errorCode": "INVALID_REQUEST",
                "detail": str(error),
                "schedules": [],
                "preservedSchedules": [],
                "unscheduledTasks": [],
                "changes": [],
            },
        )

    except Exception as error:
        return JSONResponse(
            status_code=500,
            content={
                "success": False,
                "requestId": request.requestId,
                "message": (
                    "AI 일정 생성 중 "
                    "오류가 발생했습니다."
                ),
                "errorCode": "SCHEDULING_ERROR",
                "detail": str(error),
                "schedules": [],
                "preservedSchedules": [],
                "unscheduledTasks": [],
                "changes": [],
            },

        )

@app.post("/ai/schedules/replan")
def replan_schedule(
    request: ReplanScheduleRequest,
) -> Any:
    """
    완료, 미루기, 긴급 일정, locked 상태를
    반영하여 남은 일정을 재배치한다.
    """

    try:
        internal_payload = (
            _request_to_replan_internal_payload(
                request
            )
        )

        result = replan_api_from_payload(
            internal_payload=internal_payload,
            existing_schedules=(
                request.existingSchedules
            ),
            completed_task_ids=(
                request.completedTaskIds
            ),
        )

        result["requestId"] = request.requestId
        result["userId"] = request.userId
        result["weekStartDate"] = (
            request.weekStartDate.isoformat()
        )
        result["weekEndDate"] = (
            request.weekEndDate.isoformat()
        )
        result["timezone"] = request.timezone

        return result

    except (
        KeyError,
        TypeError,
        ValueError,
    ) as error:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "requestId": request.requestId,
                "message": (
                    "일정 재배치 요청값이 "
                    "올바르지 않습니다."
                ),
                "errorCode": "INVALID_REQUEST",
                "detail": str(error),
                "schedules": [],
                "preservedSchedules": [],
                "finalSchedules": [],
                "unscheduledTasks": [],
                "changes": [],
            },
        )

    except Exception as error:
        return JSONResponse(
            status_code=500,
            content={
                "success": False,
                "requestId": request.requestId,
                "message": (
                    "AI 일정 재배치 중 "
                    "오류가 발생했습니다."
                ),
                "errorCode": "REPLAN_ERROR",
                "detail": str(error),
                "schedules": [],
                "preservedSchedules": [],
                "finalSchedules": [],
                "unscheduledTasks": [],
                "changes": [],
            },
        )