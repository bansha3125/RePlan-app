from __future__ import annotations

from datetime import datetime, time
from typing import Any, Optional

from .models import CalendarBlock, SchedulePreferences, Task
from .scheduler import schedule_tasks


def _dt(value: Any) -> datetime:
    if isinstance(value, datetime):
        return value

    return datetime.fromisoformat(str(value))


def _time(value: Any) -> time:
    if isinstance(value, time):
        return value

    return time.fromisoformat(str(value))


def _extract_step_order(block_id: str) -> int:
    """
    기존 block_id의 마지막 숫자를 stepOrder로 변환한다.

    예:
    generated:task-1:1
    → 1

    generated:task-1:step-2
    → 2
    """

    last_part = block_id.rsplit(":", 1)[-1]

    if last_part.startswith("step-"):
        last_part = last_part.replace("step-", "", 1)

    try:
        return int(last_part)
    except ValueError:
        return 1


def _reason_code_from_reason(
    reason: Optional[str],
) -> Optional[str]:
    """
    현재 스케줄러가 만든 reason을 이용해
    기본 reasonCode를 생성한다.
    """

    if not reason:
        return None

    if "마감 임박" in reason:
        return "NEAR_DEADLINE"

    if "선행" in reason:
        return "PREREQUISITE_ORDER"

    if "긴 작업 분할" in reason:
        return "CONTINUOUS_TIME_REQUIRED"

    return None

def _block_to_backend(
    block: dict[str, Any],
    step_metadata: Optional[
        dict[str, Any]
    ] = None,
) -> dict[str, Any]:
    """
    스케줄러 내부 snake_case 결과를
    백엔드용 camelCase 결과로 변환한다.
    """

    source = str(
        block.get(
            "source",
            "generated",
        )
    ).upper()

    raw_task_id = block.get(
        "task_id"
    )

    task_id = raw_task_id

    original_block_id = str(
        block["block_id"]
    )

    step_order = (
        _extract_step_order(
            original_block_id
        )
        if source == "GENERATED"
        else None
    )

    metadata = None

    if (
        source == "GENERATED"
        and raw_task_id is not None
        and step_metadata
    ):
        metadata = step_metadata.get(
            str(raw_task_id)
        )

    # -----------------------------------------
    # Replan에서 보존되는 기존 일정
    #
    # 기존 blockId를 절대로 바꾸지 않음
    # -----------------------------------------
    if (
        source == "GENERATED"
        and bool(
            block.get(
                "locked",
                False,
            )
        )
    ):
        block_id = (
            original_block_id
        )

    # -----------------------------------------
    # Gemini로 최초 분해한 세부 단계
    # -----------------------------------------
    elif metadata is not None:

        task_id = str(
            metadata["parentTaskId"]
        )

        step_order = int(
            metadata["stepOrder"]
        )

        block_id = (
            f"generated:{task_id}:"
            f"step-{step_order}"
        )

    # -----------------------------------------
    # 일반 AI 일정 최초 생성
    # -----------------------------------------
    elif (
        source == "GENERATED"
        and raw_task_id is not None
    ):
        block_id = (
            f"generated:{raw_task_id}:"
            f"step-{step_order}"
        )

    else:
        block_id = (
            original_block_id
        )

    start_time = _dt(
        block["start"]
    )

    end_time = _dt(
        block["end"]
    )

    reason = block.get(
        "reason"
    )

    return {
        "blockId": block_id,
        "taskId": task_id,
        "title": block["title"],
        "stepOrder": step_order,

        "startTime": (
            start_time.isoformat(
                timespec="seconds"
            )
        ),

        "endTime": (
            end_time.isoformat(
                timespec="seconds"
            )
        ),

        "source": source,

        "locked": bool(
            block.get(
                "locked",
                False,
            )
        ),

        "completed": bool(
            block.get(
                "completed",
                False,
            )
        ),

        "reasonCode": (
            _reason_code_from_reason(
                reason
            )
        ),

        "reason": reason,
    }

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
        task_id_by_order[order] = (
            f"{parent_task_id}-step-{order}"
        )

    tasks: list[Task] = []

    for step in steps:
        order = int(step["order"])
        depends_on_order = step.get("depends_on_order")

        prerequisite_ids: list[str] = []

        if depends_on_order is not None:
            depends_on_order = int(depends_on_order)

            prerequisite_id = task_id_by_order.get(
                depends_on_order
            )

            if prerequisite_id is not None:
                prerequisite_ids.append(
                    prerequisite_id
                )

        task = Task(
            id=task_id_by_order[order],
            title=str(step["title"]),
            deadline=deadline,
            estimated_minutes=max(
                1,
                int(step["estimated_minutes"]),
            ),
            priority=max(
                1,
                min(int(priority), 5),
            ),
            difficulty=max(
                1,
                min(
                    int(step.get("difficulty", 3)),
                    5,
                ),
            ),
            focus_required=max(
                1,
                min(
                    int(
                        step.get(
                            "focus_required",
                            3,
                        )
                    ),
                    5,
                ),
            ),
            prerequisite_ids=prerequisite_ids,
            category=category,
            splittable=True,
        )

        tasks.append(task)

    return tasks


def schedule_from_payload(
    payload: dict[str, Any],
) -> dict[str, Any]:
    """
    백엔드 연결용 함수.
    dict/JSON 입력을 받아 dict/JSON 출력으로 반환한다.
    """

    tasks = [
        Task(
            id=str(item["id"]),
            title=item["title"],
            deadline=_dt(item["deadline"]),
            estimated_minutes=int(
                item["estimated_minutes"]
            ),
            priority=int(
                item.get("priority", 3)
            ),
            difficulty=int(
                item.get("difficulty", 3)
            ),
            focus_required=int(
                item.get("focus_required", 3)
            ),
            postpone_count=int(
                item.get("postpone_count", 0)
            ),
            prerequisite_ids=[
                str(value)
                for value in item.get(
                    "prerequisite_ids",
                    [],
                )
            ],
            category=item.get(
                "category",
                "기타",
            ),
            course_id=item.get("course_id"),
            week_order=item.get("week_order"),
            task_type=item.get(
                "task_type",
                "general",
            ),
            lecture_start=(
                _dt(item["lecture_start"])
                if item.get("lecture_start")
                else None
            ),
            splittable=bool(
                item.get("splittable", True)
            ),
            min_block_minutes=int(
                item.get(
                    "min_block_minutes",
                    30,
                )
            ),
            max_block_minutes=int(
                item.get(
                    "max_block_minutes",
                    90,
                )
            ),
            completed_minutes=int(
                item.get(
                    "completed_minutes",
                    0,
                )
            ),
            completed=bool(
                item.get("completed", False)
            ),
        )
        for item in payload.get("tasks", [])
    ]

    blocks = [
        CalendarBlock(
            block_id=str(item["block_id"]),
            title=item["title"],
            start=_dt(item["start"]),
            end=_dt(item["end"]),
            source=item.get(
                "source",
                "fixed",
            ),
            task_id=(
                str(item["task_id"])
                if item.get("task_id") is not None
                else None
            ),
            locked=bool(
                item.get("locked", True)
            ),
            completed=bool(
                item.get("completed", False)
            ),
            reason=item.get("reason"),
        )
        for item in payload.get(
            "existing_blocks",
            [],
        )
    ]

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

    return schedule_tasks(
        tasks,
        blocks,
        preferences,
        now,
    ).to_dict()

def _warning_to_dict(
    warning: Any,
) -> dict[str, Any]:
    """
    경고가 dict이거나 객체여도
    공통 dict 형태로 변환한다.
    """

    if isinstance(warning, dict):
        return warning

    if hasattr(warning, "to_dict"):
        return warning.to_dict()

    return {
        "code": "SCHEDULING_WARNING",
        "message": str(warning),
        "details": {},
    }


def _safe_int(
    value: Any,
    default: int = 0,
) -> int:
    """
    숫자로 변환할 수 없는 값은 기본값으로 처리한다.
    """

    try:
        return int(value)
    except (TypeError, ValueError):
        return default

def _warning_to_unscheduled_task(
    warning_value: Any,
    task_title_by_id: dict[str, str],
    task_required_minutes_by_id: dict[str, int],
) -> dict[str, Any]:
    """
    기존 스케줄러의 warning을
    백엔드용 unscheduledTask로 변환한다.
    """

    warning = _warning_to_dict(warning_value)
    details = warning.get("details") or {}

    original_code = str(
        warning.get("code", "SCHEDULING_WARNING")
    )

    code_mapping = {
        "UNSCHEDULABLE_TASK": "INSUFFICIENT_TIME",
        "DEADLINE_ALREADY_PASSED": "DEADLINE_PASSED",
        "PREREQUISITE_NOT_SCHEDULED": "DEPENDENCY_CONFLICT",
        "DEPENDENCY_CYCLE": "DEPENDENCY_CYCLE",
        "NO_AVAILABLE_SLOT": "NO_AVAILABLE_SLOT",
        "INVALID_ESTIMATED_TIME": "INVALID_ESTIMATED_TIME",
    }

    reason_code = code_mapping.get(
        original_code,
        original_code,
    )

    task_id = warning.get(
        "taskId",
        warning.get(
            "task_id",
            details.get(
                "taskId",
                details.get("task_id"),
            ),
        ),
    )

    title = warning.get(
        "title",
        details.get("title", ""),
    )

    if not title and task_id is not None:
        title = task_title_by_id.get(
            str(task_id),
            "",
        )

    required_minutes = _safe_int(
        warning.get(
            "requiredMinutes",
            warning.get(
                "required_minutes",
                details.get(
                    "requiredMinutes",
                    details.get(
                        "required_minutes",
                        0,
                    ),
                ),
            ),
        )
    )

    # 마감 경과 경고처럼 details에 시간이 없으면
    # 요청 tasks의 estimated_minutes를 사용한다.
    if required_minutes <= 0 and task_id is not None:
        required_minutes = (
            task_required_minutes_by_id.get(
                str(task_id),
                0,
            )
        )

    available_minutes = _safe_int(
        warning.get(
            "availableMinutes",
            warning.get(
                "available_minutes",
                details.get(
                    "availableMinutes",
                    details.get(
                        "available_minutes",
                        0,
                    ),
                ),
            ),
        )
    )

    reason = str(
        warning.get(
            "message",
            "작업을 배치할 수 없습니다.",
        )
    )

    if (
        reason_code == "INSUFFICIENT_TIME"
        and required_minutes > available_minutes
    ):
        shortage_minutes = (
            required_minutes - available_minutes
        )

        reason = (
            f"마감일까지 {shortage_minutes}분의 "
            f"추가 시간이 필요합니다."
        )

    return {
        "taskId": task_id,
        "title": title,
        "requiredMinutes": required_minutes,
        "availableMinutes": available_minutes,
        "reasonCode": reason_code,
        "reason": reason,
    }


def schedule_api_from_payload(
    payload: dict[str, Any],
) -> dict[str, Any]:
    """
    기존 스케줄링 결과를
    백엔드 API 규격으로 변환한다.
    """

    raw_result = schedule_from_payload(payload)

    step_metadata = payload.get(
        "step_metadata",
        {},
    )
    schedules = [
        _block_to_backend(
            block,
            step_metadata,
        )
        for block in raw_result.get(
            "blocks",
            [],
        )
    ]

    preserved_schedules = [
        _block_to_backend(
            block,
            step_metadata,
        )
        for block in raw_result.get(
            "preserved_blocks",
            [],
        )
    ]

    warnings = raw_result.get("warnings", [])

    task_title_by_id = {
        str(task.get("id")): str(
            task.get("title", "")
        )
        for task in payload.get("tasks", [])
    }

    task_required_minutes_by_id = {
        str(task.get("id")): _safe_int(
            task.get("estimated_minutes", 0)
        )
        for task in payload.get("tasks", [])
    }

    unscheduled_tasks = [
        _warning_to_unscheduled_task(
            warning,
            task_title_by_id,
            task_required_minutes_by_id,
        )
        for warning in warnings
    ]

    schedule_count = len(schedules)
    unscheduled_count = len(unscheduled_tasks)

    if schedule_count > 0 and unscheduled_count > 0:
        message = (
            f"일정 {schedule_count}개를 배치하고 "
            f"{unscheduled_count}개를 "
            f"배치하지 못했습니다."
        )

    elif schedule_count == 0 and unscheduled_count > 0:
        message = (
            f"작업 {unscheduled_count}개를 "
            f"배치하지 못했습니다."
        )

    else:
        message = (
            f"일정 {schedule_count}개를 "
            f"생성했습니다."
        )

    return {
        # 알고리즘이 정상 실행됐다면
        # 일부 작업을 못 넣어도 success는 True
        "success": True,
        "message": message,
        "schedules": schedules,
        "preservedSchedules": preserved_schedules,
        "unscheduledTasks": unscheduled_tasks,
        "changes": [],
        "warnings": warnings,
        "scores": raw_result.get("scores", {}),
        "summary": {
            "createdCount": schedule_count,
            "preservedCount": len(
                preserved_schedules
            ),
            "unscheduledCount": (
                unscheduled_count
            ),
            "warningCount": len(warnings),
        },
    }

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
            "필수 입력값이 없습니다: "
            + ", ".join(missing_fields)
        )

    parent_task_id = str(
        payload["parent_task_id"]
    )

    task_title = str(
        payload["task_title"]
    ).strip()

    desired_steps = max(
        1,
        min(
            int(
                payload.get(
                    "desired_steps",
                    5,
                )
            ),
            10,
        ),
    )

    total_estimated_minutes = int(
        payload["total_estimated_minutes"]
    )

    deadline = _dt(payload["deadline"])

    priority = max(
        1,
        min(
            int(payload.get("priority", 3)),
            5,
        ),
    )

    category = str(
        payload.get("category", "기타")
    )

    context = str(
        payload.get("context", "")
    )

    # 1. Gemini 큰 작업 분해
    decomposition_result = (
        gemini_service.decompose_task(
            task_title=task_title,
            desired_steps=desired_steps,
            total_estimated_minutes=(
                total_estimated_minutes
            ),
            context=context,
        )
    )

    steps = decomposition_result.data.get(
        "steps",
        [],
    )

    if not steps:
        raise RuntimeError(
            "Gemini 작업 분해 결과에 "
            "steps가 없습니다."
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
            source=item.get(
                "source",
                "fixed",
            ),
            task_id=(
                str(item["task_id"])
                if item.get("task_id")
                is not None
                else None
            ),
            locked=bool(
                item.get("locked", True)
            ),
            completed=bool(
                item.get("completed", False)
            ),
            reason=item.get("reason"),
        )
        for item in payload.get(
            "existing_blocks",
            [],
        )
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
            "used_fallback": (
                decomposition_result.used_fallback
            ),
            "error": decomposition_result.error,
        },
        "generated_tasks": [
            {
                "id": task.id,
                "title": task.title,
                "deadline": (
                    task.deadline.isoformat()
                ),
                "estimated_minutes": (
                    task.estimated_minutes
                ),
                "priority": task.priority,
                "difficulty": task.difficulty,
                "focus_required": (
                    task.focus_required
                ),
                "prerequisite_ids": (
                    task.prerequisite_ids
                ),
                "category": task.category,
            }
            for task in tasks
        ],
        "schedule": schedule_data,
    }


def _to_iso_string(value: Any) -> Any:
    """
    datetime 값은 ISO 문자열로 변환하고,
    이미 문자열이면 그대로 반환한다.
    """

    if isinstance(value, datetime):
        return value.isoformat(
            timespec="seconds"
        )

    return value


def _normalize_backend_schedule(
    item: dict[str, Any],
) -> dict[str, Any]:
    """
    camelCase 또는 snake_case 일정을
    changes 비교용 공통 구조로 변환한다.
    """

    return {
        "blockId": str(
            item.get(
                "blockId",
                item.get("block_id", ""),
            )
        ),
        "taskId": item.get(
            "taskId",
            item.get("task_id"),
        ),
        "title": item.get("title", ""),
        "startTime": _to_iso_string(
            item.get(
                "startTime",
                item.get("start"),
            )
        ),
        "endTime": _to_iso_string(
            item.get(
                "endTime",
                item.get("end"),
            )
        ),
        "source": str(
            item.get(
                "source",
                "GENERATED",
            )
        ).upper(),
        "locked": bool(
            item.get("locked", False)
        ),
        "reasonCode": item.get(
            "reasonCode"
        ),
        "reason": item.get("reason"),
    }


def _build_schedule_changes(
    before_schedules: list[dict[str, Any]],
    after_schedules: list[dict[str, Any]],
    completed_task_ids: list[Any],
) -> list[dict[str, Any]]:
    """
    재배치 전후 일정을 blockId로 비교해서
    CREATED, SPLIT, MOVED, KEPT, REMOVED를 생성한다.
    """

    completed_ids = {
        str(task_id)
        for task_id in completed_task_ids
    }

    before_map: dict[str, dict[str, Any]] = {}

    for item in before_schedules:
        normalized = _normalize_backend_schedule(
            item
        )

        if not normalized["blockId"]:
            continue

        # 리플레이에서는 AI 생성 일정만 비교
        if normalized["source"] != "GENERATED":
            continue

        before_map[
            normalized["blockId"]
        ] = normalized

    after_map: dict[str, dict[str, Any]] = {}

    for item in after_schedules:
        normalized = _normalize_backend_schedule(
            item
        )

        if not normalized["blockId"]:
            continue

        if normalized["source"] != "GENERATED":
            continue

        after_map[
            normalized["blockId"]
        ] = normalized

    changes: list[dict[str, Any]] = []
    sequence = 1

    # 생성, 분할, 이동, 유지 검사
    for block_id, after in after_map.items():
        before = before_map.get(block_id)

        if before is None:
            step_order_value = (
                _extract_step_order(block_id)
            )

            # 첫 번째 블록은 CREATED,
            # 두 번째 블록부터 SPLIT
            is_split = (
                after.get("source") == "GENERATED"
                and step_order_value > 1
            )

            if is_split:
                action = "SPLIT"
                reason_code = "TASK_SPLIT"
                reason = (
                    "긴 작업을 여러 일정 블록으로 "
                    "나누어 배치했습니다."
                )

            else:
                action = "CREATED"
                reason_code = (
                    after.get("reasonCode")
                    or "SCHEDULE_CREATED"
                )
                reason = (
                    after.get("reason")
                    or "새로운 일정을 생성했습니다."
                )

        elif (
            before["startTime"]
            != after["startTime"]
            or before["endTime"]
            != after["endTime"]
        ):
            action = "MOVED"
            reason_code = (
                after.get("reasonCode")
                or "REPLANNED"
            )
            reason = (
                after.get("reason")
                or (
                    "변경된 조건을 반영하여 "
                    "이동했습니다."
                )
            )

        else:
            action = "KEPT"

            if after.get("locked"):
                reason_code = "USER_LOCKED"
                reason = (
                    "사용자가 고정한 일정이므로 "
                    "기존 시간을 유지했습니다."
                )

            else:
                reason_code = "UNCHANGED"
                reason = (
                    "기존 시간이 적절하여 "
                    "그대로 유지했습니다."
                )

        changes.append({
            "sequence": sequence,
            "action": action,
            "taskId": after.get("taskId"),
            "blockId": block_id,
            "title": after.get("title", ""),
            "beforeStartTime": (
                before.get("startTime")
                if before
                else None
            ),
            "beforeEndTime": (
                before.get("endTime")
                if before
                else None
            ),
            "afterStartTime": after.get(
                "startTime"
            ),
            "afterEndTime": after.get(
                "endTime"
            ),
            "reasonCode": reason_code,
            "reason": reason,
        })

        sequence += 1

    # 기존에는 있었지만 새 결과에는 없는 일정
    for block_id, before in before_map.items():
        if block_id in after_map:
            continue

        task_id = before.get("taskId")

        if str(task_id) in completed_ids:
            reason_code = "TASK_COMPLETED"
            reason = (
                "완료한 작업이므로 "
                "재배치 대상에서 제외했습니다."
            )

        else:
            reason_code = "REMOVED_FROM_PLAN"
            reason = (
                "변경된 조건에 따라 "
                "기존 일정에서 제거했습니다."
            )

        changes.append({
            "sequence": sequence,
            "action": "REMOVED",
            "taskId": task_id,
            "blockId": block_id,
            "title": before.get("title", ""),
            "beforeStartTime": before.get(
                "startTime"
            ),
            "beforeEndTime": before.get(
                "endTime"
            ),
            "afterStartTime": None,
            "afterEndTime": None,
            "reasonCode": reason_code,
            "reason": reason,
        })

        sequence += 1

    # 같은 시간일 때 사용할 action 우선순위
    action_priority = {
        "REMOVED": 0,
        "MOVED": 1,
        "CREATED": 2,
        "SPLIT": 3,
        "KEPT": 4,
    }

    def change_sort_key(
        change: dict[str, Any],
    ) -> tuple[str, int, str, str]:
        """
        afterStartTime이 있으면 변경 후 시간을 사용하고,
        REMOVED처럼 없으면 beforeStartTime을 사용한다.
        """

        sort_time = (
            change.get("afterStartTime")
            or change.get("beforeStartTime")
            or ""
        )

        return (
            str(sort_time),
            action_priority.get(
                str(change.get("action")),
                99,
            ),
            str(change.get("taskId") or ""),
            str(change.get("blockId") or ""),
        )

    # 변경 시간순으로 정렬
    changes.sort(
        key=change_sort_key
    )

    # 정렬 후 sequence를 다시 1부터 부여
    for index, change in enumerate(
        changes,
        start=1,
    ):
        change["sequence"] = index

    return changes




def replan_api_from_payload(
    internal_payload: dict[str, Any],
    existing_schedules: list[dict[str, Any]],
    completed_task_ids: list[Any],
) -> dict[str, Any]:
    """
    재배치용 내부 입력으로 스케줄러를 실행하고,
    최종 일정 및 changes 배열을 생성한다.
    """
    result = schedule_api_from_payload(
        internal_payload
    )

    # -----------------------------------------
    # Replan용 임시 taskId를
    # 원래 taskId / blockId / stepOrder로 복구
    # -----------------------------------------

    replan_metadata = (
        internal_payload.get(
            "replan_block_metadata",
            {},
        )
    )

    for schedule in result.get(
        "schedules",
        [],
    ):
        internal_task_id = str(
            schedule.get("taskId")
        )

        metadata = (
            replan_metadata.get(
                internal_task_id
            )
        )

        if metadata is None:
            continue

        schedule["taskId"] = (
            metadata["parentTaskId"]
        )

        schedule["blockId"] = (
            metadata["blockId"]
        )

        schedule["stepOrder"] = (
            metadata["stepOrder"]
        )

        schedule["title"] = (
            metadata["title"]
        )

    # 미배치 결과에서도 내부 ID 제거
    for unscheduled in result.get(
        "unscheduledTasks",
        [],
    ):
        internal_task_id = str(
            unscheduled.get("taskId")
        )

        metadata = (
            replan_metadata.get(
                internal_task_id
            )
        )

        if metadata is None:
            continue

        unscheduled["taskId"] = (
            metadata["parentTaskId"]
        )

        unscheduled["blockId"] = (
            metadata["blockId"]
        )

        unscheduled["title"] = (
            metadata["title"]
        )


    final_schedules = (
        result.get(
            "preservedSchedules",
            [],
        )
        + result.get(
            "schedules",
            [],
        )
    )

    changes = _build_schedule_changes(
        before_schedules=existing_schedules,
        after_schedules=final_schedules,
        completed_task_ids=(
            completed_task_ids
        ),
    )

    created_count = sum(
        1
        for change in changes
        if change["action"] == "CREATED"
    )

    split_count = sum(
        1
        for change in changes
        if change["action"] == "SPLIT"
    )

    moved_count = sum(
        1
        for change in changes
        if change["action"] == "MOVED"
    )

    kept_count = sum(
        1
        for change in changes
        if change["action"] == "KEPT"
    )

    removed_count = sum(
        1
        for change in changes
        if change["action"] == "REMOVED"
    )

    result["message"] = (
        "일정 재배치가 완료되었습니다."
    )
    result["changes"] = changes
    result["finalSchedules"] = (
        final_schedules
    )

    result.setdefault("summary", {})

    result["summary"].update({
        "createdCount": created_count,
        "splitCount": split_count,
        "movedCount": moved_count,
        "keptCount": kept_count,
        "removedCount": removed_count,
    })

    return result