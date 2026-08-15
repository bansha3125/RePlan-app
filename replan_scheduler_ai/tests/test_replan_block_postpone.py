from main import (
    ReplanScheduleRequest,
    _request_to_replan_internal_payload,
)


def make_request():

    return ReplanScheduleRequest(
        requestId="replan-block-test-001",
        userId=1,

        weekStartDate="2026-08-17",
        weekEndDate="2026-08-23",

        timezone="Asia/Seoul",

        replanFromTime=(
            "2026-08-17T09:00:00"
        ),

        completedTaskIds=[],
        postponedTaskIds=[],

        # 3단계만 미루기
        postponedBlockIds=[
            "generated:5:step-3"
        ],

        tasks=[
            {
                "taskId": 5,
                "title": "알고리즘 과제",
                "estimatedMinutes": 180,

                "deadline": (
                    "2026-08-18T22:00:00"
                ),

                "priority": 1,
                "difficulty": 3,
                "focusRequired": 3,

                "completedMinutes": 0,
                "completed": False,
            }
        ],

        fixedSchedules=[],

        existingSchedules=[
            {
                "blockId": (
                    "generated:5:step-1"
                ),
                "taskId": "5",
                "title": "알고리즘 1단계",
                "stepOrder": 1,

                "startTime": (
                    "2026-08-17T10:00:00"
                ),

                "endTime": (
                    "2026-08-17T11:00:00"
                ),

                "source": "GENERATED",
                "locked": False,
                "completed": False,
            },

            {
                "blockId": (
                    "generated:5:step-2"
                ),
                "taskId": "5",
                "title": "알고리즘 2단계",
                "stepOrder": 2,

                "startTime": (
                    "2026-08-17T11:00:00"
                ),

                "endTime": (
                    "2026-08-17T12:00:00"
                ),

                "source": "GENERATED",
                "locked": False,
                "completed": False,
            },

            {
                "blockId": (
                    "generated:5:step-3"
                ),
                "taskId": "5",
                "title": "알고리즘 3단계",
                "stepOrder": 3,

                "startTime": (
                    "2026-08-17T14:00:00"
                ),

                "endTime": (
                    "2026-08-17T15:00:00"
                ),

                "source": "GENERATED",
                "locked": False,
                "completed": False,
            },
        ],
    )


def test_only_selected_block_is_replanned():

    request = make_request()

    payload = (
        _request_to_replan_internal_payload(
            request
        )
    )

    tasks = payload["tasks"]

    # 재배치 대상은 3단계 하나뿐이어야 함
    assert len(tasks) == 1

    moved = tasks[0]

    assert (
        moved["id"]
        == (
            "replan-block::"
            "generated:5:step-3"
        )
    )

    assert (
        moved["title"]
        == "알고리즘 3단계"
    )

    assert (
        moved["estimated_minutes"]
        == 60
    )

    # 기존 단계를 다시 쪼개면 안 됨
    assert (
        moved["splittable"]
        is False
    )


def test_step1_and_step2_are_preserved():

    request = make_request()

    payload = (
        _request_to_replan_internal_payload(
            request
        )
    )

    preserved_ids = {
        block["block_id"]
        for block
        in payload["existing_blocks"]
    }

    assert (
        "generated:5:step-1"
        in preserved_ids
    )

    assert (
        "generated:5:step-2"
        in preserved_ids
    )

    # 미루기 선택한 step3은
    # 기존 위치에 남아 있으면 안 됨
    assert (
        "generated:5:step-3"
        not in preserved_ids
    )


def test_parent_task_is_not_created_again():

    request = make_request()

    payload = (
        _request_to_replan_internal_payload(
            request
        )
    )

    task_ids = {
        task["id"]
        for task
        in payload["tasks"]
    }

    # 부모 과제 전체가 다시 생성되면
    # 예전 3단계 -> 2단계 문제가 재발할 수 있음
    assert "5" not in task_ids