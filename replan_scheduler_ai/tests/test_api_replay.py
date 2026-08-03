import unittest
from datetime import datetime, timedelta, time

from api_test_base import BaseAPITest

from ai_scheduler.integration import (
    _build_schedule_changes,
)


class TestAPIReplay(BaseAPITest):
    def test_new_task_creates_created_change(self):
        """
        기존 일정에 없던 새 작업이 재배치되면
        changes에 CREATED가 기록되는지 테스트한다.
        """

        test_date = (datetime.now() + timedelta(days=1)).date()

        week_start = test_date - timedelta(
            days=test_date.weekday()
        )
        week_end = week_start + timedelta(days=6)

        def make_datetime(hour: int, minute: int = 0) -> str:
            return datetime.combine(
                test_date,
                time(hour=hour, minute=minute),
            ).isoformat()

        request_body = {
            "requestId": "created-change-test-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",
            "replanFromTime": make_datetime(8),

            "completedTaskIds": [],
            "postponedTaskIds": [],

            "tasks": [
                {
                    "taskId": 5,
                    "title": "새로 추가된 보고서 작성",
                    "estimatedMinutes": 60,
                    "deadline": make_datetime(18),
                    "priority": 2,
                    "difficulty": 2,
                    "focusRequired": 2,
                }
            ],

            "fixedSchedules": [],

            # 기존 일정에는 taskId=5가 없음
            "existingSchedules": [],
        }

        response = self.client.post(
            "/ai/schedules/replan",
            json=request_body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=f"CREATED 테스트 요청 실패: {response.text}",
        )

        data = response.json()

    

        self.assertTrue(
            data.get("success"),
            msg=f"재배치 응답이 실패함: {data}",
        )

        schedules = data.get("schedules", [])

        # 새 작업이 실제 일정에 생성됐는지 확인
        created_schedule = None

        for schedule in schedules:
            if str(schedule.get("taskId")) == "5":
                created_schedule = schedule
                break

        self.assertIsNotNone(
            created_schedule,
            msg=f"새 taskId=5 일정이 생성되지 않음: {schedules}",
        )

        changes = data.get("changes", [])

        # taskId=5의 CREATED 변경 기록 찾기
        created_change = None

        for change in changes:
            if (
                str(change.get("taskId")) == "5"
                and change.get("action") == "CREATED"
            ):
                created_change = change
                break

        self.assertIsNotNone(
            created_change,
            msg=f"taskId=5의 CREATED 기록이 없음: {changes}",
        )

        # 새로 생성된 일정이므로 변경 전 시간은 없어야 함
        self.assertIsNone(
            created_change.get("beforeStartTime"),
            msg=f"CREATED의 beforeStartTime이 비어 있지 않음: {created_change}",
        )

        self.assertIsNone(
            created_change.get("beforeEndTime"),
            msg=f"CREATED의 beforeEndTime이 비어 있지 않음: {created_change}",
        )

        # 생성된 이후 시간은 존재해야 함
        self.assertIsNotNone(
            created_change.get("afterStartTime"),
            msg=f"CREATED의 afterStartTime이 없음: {created_change}",
        )

        self.assertIsNotNone(
            created_change.get("afterEndTime"),
            msg=f"CREATED의 afterEndTime이 없음: {created_change}",
        )

        # 생성된 일정의 시간과 changes의 시간이 같은지 확인
        self.assertEqual(
            created_change.get("afterStartTime"),
            created_schedule.get("startTime"),
            msg="CREATED 기록과 실제 일정의 시작 시간이 다름",
        )

        self.assertEqual(
            created_change.get("afterEndTime"),
            created_schedule.get("endTime"),
            msg="CREATED 기록과 실제 일정의 종료 시간이 다름",
        )

        # sequence가 존재하고 1 이상인지 확인
        self.assertIsInstance(
            created_change.get("sequence"),
            int,
            msg=f"CREATED의 sequence가 정수가 아님: {created_change}",
        )

        self.assertGreaterEqual(
            created_change.get("sequence"),
            1,
            msg=f"CREATED의 sequence가 1보다 작음: {created_change}",
        )


    def test_long_task_creates_split_change(self):
        """
        180분짜리 새 작업이 여러 일정 블록으로 나뉘고,
        changes에 CREATED와 SPLIT이 기록되는지 테스트한다.
        """

        test_date = (
            datetime.now() + timedelta(days=1)
        ).date()

        week_start = test_date - timedelta(
            days=test_date.weekday()
        )
        week_end = week_start + timedelta(days=6)

        def make_datetime(
            hour: int,
            minute: int = 0,
        ) -> str:
            return datetime.combine(
                test_date,
                time(hour=hour, minute=minute),
            ).isoformat()

        request_body = {
            "requestId": "split-change-test-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",
            "replanFromTime": make_datetime(9),

            "completedTaskIds": [],
            "postponedTaskIds": [],

            "tasks": [
                {
                    "taskId": 6,
                    "title": "긴 발표 자료 작성",
                    "estimatedMinutes": 180,
                    "deadline": make_datetime(20),
                    "priority": 2,
                    "difficulty": 3,
                    "focusRequired": 3,
                }
            ],

            "fixedSchedules": [],
            "existingSchedules": [],
        }

        response = self.client.post(
            "/ai/schedules/replan",
            json=request_body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=f"SPLIT 테스트 요청 실패: {response.text}",
        )

        data = response.json()

     

        self.assertTrue(
            data.get("success"),
            msg=f"재배치 응답이 실패함: {data}",
        )

        schedules = data.get("schedules", [])

        # taskId=6에 해당하는 일정 블록만 찾기
        task_blocks = [
            schedule
            for schedule in schedules
            if str(schedule.get("taskId")) == "6"
        ]

        # 긴 작업이 2개 이상의 블록으로 분할돼야 함
        self.assertGreaterEqual(
            len(task_blocks),
            2,
            msg=(
                "180분 작업이 여러 블록으로 "
                f"분할되지 않음: {task_blocks}"
            ),
        )

        # blockId가 서로 달라야 함
        block_ids = [
            block.get("blockId")
            for block in task_blocks
        ]

        self.assertEqual(
            len(block_ids),
            len(set(block_ids)),
            msg=f"분할 블록의 blockId가 중복됨: {block_ids}",
        )

        # 분할된 모든 블록의 시간 합이 180분인지 확인
        total_minutes = 0

        for block in task_blocks:
            start_time = datetime.fromisoformat(
                block["startTime"]
            )
            end_time = datetime.fromisoformat(
                block["endTime"]
            )

            total_minutes += int(
                (
                    end_time - start_time
                ).total_seconds()
                / 60
            )

        self.assertEqual(
            total_minutes,
            180,
            msg=(
                "분할 블록의 전체 시간 합이 "
                f"180분이 아님: {total_minutes}분"
            ),
        )

        changes = data.get("changes", [])

        task_changes = [
            change
            for change in changes
            if str(change.get("taskId")) == "6"
        ]

        # 첫 번째 블록은 CREATED
        created_exists = any(
            change.get("action") == "CREATED"
            for change in task_changes
        )

        self.assertTrue(
            created_exists,
            msg=f"분할 작업의 CREATED 기록이 없음: {task_changes}",
        )

        # 두 번째 이후 블록에는 SPLIT이 있어야 함
        split_changes = [
            change
            for change in task_changes
            if change.get("action") == "SPLIT"
        ]

        self.assertGreaterEqual(
            len(split_changes),
            1,
            msg=f"SPLIT 변경 기록이 없음: {task_changes}",
        )

        for split_change in split_changes:
            self.assertIsNone(
                split_change.get("beforeStartTime"),
                msg=(
                    "새 분할 블록의 beforeStartTime이 "
                    f"비어 있지 않음: {split_change}"
                ),
            )

            self.assertIsNotNone(
                split_change.get("afterStartTime"),
                msg=(
                    "SPLIT의 afterStartTime이 없음: "
                    f"{split_change}"
                ),
            )

            self.assertIsNotNone(
                split_change.get("afterEndTime"),
                msg=(
                    "SPLIT의 afterEndTime이 없음: "
                    f"{split_change}"
                ),
            )

            self.assertEqual(
                split_change.get("reasonCode"),
                "TASK_SPLIT",
                msg=(
                    "SPLIT reasonCode가 올바르지 않음: "
                    f"{split_change}"
                ),
            )

        summary = data.get("summary", {})

        self.assertGreaterEqual(
            summary.get("splitCount", 0),
            1,
            msg=f"summary의 splitCount가 올바르지 않음: {summary}",
        )


    def test_change_sequence_is_sorted(self):
        """
        changes가 시간순으로 정렬되고
        sequence가 1부터 연속으로 부여되는지 테스트한다.
        """

        before_schedules = [
            {
                "blockId": "generated:2:step-1",
                "taskId": "2",
                "title": "완료한 작업",
                "startTime": "2026-08-10T09:00:00",
                "endTime": "2026-08-10T10:00:00",
                "source": "GENERATED",
                "locked": False,
            },
            {
                "blockId": "generated:1:step-1",
                "taskId": "1",
                "title": "이동할 작업",
                "startTime": "2026-08-10T10:00:00",
                "endTime": "2026-08-10T11:00:00",
                "source": "GENERATED",
                "locked": False,
            },
        ]

        after_schedules = [
            {
                "blockId": "generated:1:step-1",
                "taskId": "1",
                "title": "이동할 작업",
                "startTime": "2026-08-10T12:00:00",
                "endTime": "2026-08-10T13:00:00",
                "source": "GENERATED",
                "locked": False,
            },
            {
                "blockId": "generated:3:step-1",
                "taskId": "3",
                "title": "새로 생성된 작업",
                "startTime": "2026-08-10T11:00:00",
                "endTime": "2026-08-10T12:00:00",
                "source": "GENERATED",
                "locked": False,
            },
        ]

        changes = _build_schedule_changes(
            before_schedules=before_schedules,
            after_schedules=after_schedules,
            completed_task_ids=[2],
        )

        # sequence가 1, 2, 3 순서인지 확인
        sequences = [
            change.get("sequence")
            for change in changes
        ]

        self.assertEqual(
            sequences,
            [1, 2, 3],
            msg=f"sequence가 연속적이지 않음: {changes}",
        )

        # 같은 sequence가 중복되지 않는지 확인
        self.assertEqual(
            len(sequences),
            len(set(sequences)),
            msg=f"sequence가 중복됨: {changes}",
        )

        # 시간순 결과:
        # 09:00 REMOVED → 11:00 CREATED → 12:00 MOVED
        actions = [
            change.get("action")
            for change in changes
        ]

        self.assertEqual(
            actions,
            [
                "REMOVED",
                "CREATED",
                "MOVED",
            ],
            msg=f"changes가 시간순으로 정렬되지 않음: {changes}",
        )

        sort_times = [
            (
                change.get("afterStartTime")
                or change.get("beforeStartTime")
            )
            for change in changes
        ]

        self.assertEqual(
            sort_times,
            sorted(sort_times),
            msg=f"변경 시간이 오름차순이 아님: {changes}",
        )


if __name__ == "__main__":
    unittest.main()
