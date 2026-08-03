import unittest
from datetime import datetime, timedelta, time

from api_test_base import BaseAPITest


class TestAPIContract(BaseAPITest):
    def test_app_loads(self):
        response = self.client.get("/openapi.json")

        self.assertEqual(response.status_code, 200)

        response_data = response.json()

        self.assertIn("openapi", response_data)
        self.assertIn("paths", response_data)


    def test_required_api_paths_exist(self):
        response = self.client.get("/openapi.json")

        self.assertEqual(response.status_code, 200)

        paths = response.json().get("paths", {})

        self.assertIn("/schedules/generate", paths)
        self.assertIn("/ai/schedules/replan", paths)


    def test_string_task_id_is_supported(self):
        """
        숫자가 아닌 문자열 taskId도 generate와 replan API에서
        정상적으로 처리되는지 테스트한다.
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
                time(
                    hour=hour,
                    minute=minute,
                ),
            ).isoformat()

        string_task_id = "task-101"

        # 1. 문자열 taskId로 최초 일정 생성
        generate_body = {
            "requestId": "string-task-id-generate-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",

            "tasks": [
                {
                    "taskId": string_task_id,
                    "title": "문자열 ID 작업",
                    "estimatedMinutes": 60,
                    "deadline": make_datetime(18),
                    "priority": 2,
                    "difficulty": 2,
                    "focusRequired": 2,
                }
            ],

            "fixedSchedules": [],
            "existingSchedules": [],
        }

        generate_response = self.client.post(
            "/schedules/generate",
            json=generate_body,
        )

        self.assertEqual(
            generate_response.status_code,
            200,
            msg=(
                "문자열 taskId generate 요청 실패: "
                f"{generate_response.text}"
            ),
        )

        generate_data = generate_response.json()

        self.assertTrue(
            generate_data.get("success"),
            msg=f"generate 응답 실패: {generate_data}",
        )

        generated_schedule = None

        for schedule in generate_data.get(
            "schedules",
            [],
        ):
            if (
                str(schedule.get("taskId"))
                == string_task_id
            ):
                generated_schedule = schedule
                break

        self.assertIsNotNone(
            generated_schedule,
            msg=(
                "문자열 taskId 일정이 생성되지 않음: "
                f"{generate_data}"
            ),
        )

        self.assertTrue(
            str(
                generated_schedule.get(
                    "blockId",
                    "",
                )
            ).startswith(
                f"generated:{string_task_id}:"
            ),
            msg=(
                "문자열 taskId가 blockId에 "
                f"정상 반영되지 않음: {generated_schedule}"
            ),
        )

        # 2. 문자열 taskId를 완료 목록으로 전달
        replan_body = {
            "requestId": "string-task-id-replan-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",
            "replanFromTime": make_datetime(8),

            "completedTaskIds": [
                string_task_id
            ],
            "postponedTaskIds": [],

            "tasks": [
                {
                    "taskId": string_task_id,
                    "title": "문자열 ID 작업",
                    "estimatedMinutes": 60,
                    "deadline": make_datetime(18),
                    "priority": 2,
                    "difficulty": 2,
                    "focusRequired": 2,
                }
            ],

            "fixedSchedules": [],

            "existingSchedules": [
                {
                    **generated_schedule,
                    "completed": False,
                    "locked": False,
                }
            ],
        }

        replan_response = self.client.post(
            "/ai/schedules/replan",
            json=replan_body,
        )

        self.assertEqual(
            replan_response.status_code,
            200,
            msg=(
                "문자열 taskId replan 요청 실패: "
                f"{replan_response.text}"
            ),
        )

        replan_data = replan_response.json()

        self.assertTrue(
            replan_data.get("success"),
            msg=f"replan 응답 실패: {replan_data}",
        )

        final_task_ids = {
            str(schedule.get("taskId"))
            for schedule in replan_data.get(
                "finalSchedules",
                [],
            )
            if schedule.get("taskId") is not None
        }

        # 완료된 문자열 ID 작업이 최종 일정에서 제외돼야 함
        self.assertNotIn(
            string_task_id,
            final_task_ids,
            msg=(
                "완료한 문자열 taskId가 최종 일정에 남음: "
                f"{replan_data}"
            ),
        )

        removed_change = None

        for change in replan_data.get(
            "changes",
            [],
        ):
            if (
                str(change.get("taskId"))
                == string_task_id
                and change.get("action") == "REMOVED"
            ):
                removed_change = change
                break

        self.assertIsNotNone(
            removed_change,
            msg=(
                "문자열 taskId의 REMOVED 기록이 없음: "
                f"{replan_data.get('changes', [])}"
            ),
        )

        self.assertEqual(
            removed_change.get("reasonCode"),
            "TASK_COMPLETED",
            msg=(
                "완료 문자열 taskId의 reasonCode가 "
                f"올바르지 않음: {removed_change}"
            ),
        )


    def test_duplicate_generate_request_id_returns_cached_result(
        self,
    ):
        """
        generate API에 동일한 requestId를 두 번 보내면
        두 번째 요청이 기존 결과를 반환하는지 테스트한다.
        """

        test_date = (
            datetime.now() + timedelta(days=1)
        ).date()

        week_start = test_date - timedelta(
            days=test_date.weekday()
        )
        week_end = week_start + timedelta(days=6)

        deadline = datetime.combine(
            test_date,
            time(hour=18),
        ).isoformat()

        request_body = {
            "requestId": "duplicate-request-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",

            "tasks": [
                {
                    "taskId": "duplicate-task-1",
                    "title": "중복 요청 테스트 작업",
                    "estimatedMinutes": 60,
                    "deadline": deadline,
                    "priority": 2,
                    "difficulty": 2,
                    "focusRequired": 2,
                }
            ],

            "fixedSchedules": [],
            "existingSchedules": [],
        }

        first_response = self.client.post(
            "/schedules/generate",
            json=request_body,
        )

        second_response = self.client.post(
            "/schedules/generate",
            json=request_body,
        )

        self.assertEqual(
            first_response.status_code,
            200,
            msg=first_response.text,
        )

        self.assertEqual(
            second_response.status_code,
            200,
            msg=second_response.text,
        )

        first_data = first_response.json()
        second_data = second_response.json()

        self.assertFalse(
            first_data.get("duplicateRequest"),
            msg=(
                "첫 번째 요청이 중복 요청으로 처리됨: "
                f"{first_data}"
            ),
        )

        self.assertTrue(
            second_data.get("duplicateRequest"),
            msg=(
                "두 번째 요청이 중복으로 처리되지 않음: "
                f"{second_data}"
            ),
        )

        first_result = dict(first_data)
        second_result = dict(second_data)

        first_result.pop(
            "duplicateRequest",
            None,
        )
        second_result.pop(
            "duplicateRequest",
            None,
        )

        self.assertEqual(
            first_result,
            second_result,
            msg=(
                "동일 requestId의 응답 결과가 달라짐:\n"
                f"첫 번째: {first_data}\n"
                f"두 번째: {second_data}"
            ),
        )


    def test_duplicate_replan_request_id_returns_cached_result(
        self,
    ):
        """
        replan API에 동일한 requestId를 두 번 보내면
        두 번째 요청이 기존 결과를 반환하는지 테스트한다.
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
                time(
                    hour=hour,
                    minute=minute,
                ),
            ).isoformat()

        request_body = {
            # generate 테스트와 같은 requestId를 사용해도
            # 엔드포인트가 다르므로 충돌하면 안 됨
            "requestId": "duplicate-request-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",
            "replanFromTime": make_datetime(8),

            "completedTaskIds": [],
            "postponedTaskIds": [],

            "tasks": [
                {
                    "taskId": "duplicate-task-2",
                    "title": "중복 재배치 테스트",
                    "estimatedMinutes": 60,
                    "deadline": make_datetime(18),
                    "priority": 2,
                    "difficulty": 2,
                    "focusRequired": 2,
                }
            ],

            "fixedSchedules": [
                {
                    "fixedScheduleId": 100,
                    "title": "긴급 일정",
                    "startTime": make_datetime(9),
                    "endTime": make_datetime(11),
                }
            ],

            "existingSchedules": [
                {
                    "blockId": (
                        "generated:"
                        "duplicate-task-2:"
                        "step-1"
                    ),
                    "taskId": "duplicate-task-2",
                    "title": "중복 재배치 테스트",
                    "stepOrder": 1,
                    "startTime": make_datetime(9),
                    "endTime": make_datetime(10),
                    "source": "GENERATED",
                    "locked": False,
                    "completed": False,
                }
            ],
        }

        first_response = self.client.post(
            "/ai/schedules/replan",
            json=request_body,
        )

        second_response = self.client.post(
            "/ai/schedules/replan",
            json=request_body,
        )

        self.assertEqual(
            first_response.status_code,
            200,
            msg=first_response.text,
        )

        self.assertEqual(
            second_response.status_code,
            200,
            msg=second_response.text,
        )

        first_data = first_response.json()
        second_data = second_response.json()

        self.assertFalse(
            first_data.get("duplicateRequest"),
            msg=(
                "첫 번째 재배치 요청이 "
                f"중복으로 처리됨: {first_data}"
            ),
        )

        self.assertTrue(
            second_data.get("duplicateRequest"),
            msg=(
                "두 번째 재배치 요청이 "
                f"중복으로 처리되지 않음: {second_data}"
            ),
        )

        first_result = dict(first_data)
        second_result = dict(second_data)

        first_result.pop(
            "duplicateRequest",
            None,
        )
        second_result.pop(
            "duplicateRequest",
            None,
        )

        self.assertEqual(
            first_result,
            second_result,
            msg=(
                "동일 requestId 재배치 결과가 달라짐:\n"
                f"첫 번째: {first_data}\n"
                f"두 번째: {second_data}"
            ),
        )


if __name__ == "__main__":
    unittest.main()
