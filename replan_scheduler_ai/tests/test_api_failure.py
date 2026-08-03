import unittest
from datetime import datetime, timedelta, time

from api_test_base import BaseAPITest


class TestAPIFailure(BaseAPITest):
    def test_insufficient_time_returns_unscheduled_task(self):
        """
        마감 전 가용 시간이 작업 필요 시간보다 부족하면
        작업이 unscheduledTasks에 포함되는지 테스트한다.
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
            "requestId": "insufficient-time-test-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",

            "tasks": [
                {
                    "taskId": 3,
                    "title": "발표 연습",

                    # 가용 시간보다 확실히 큰 값으로 설정
                    "estimatedMinutes": 1200,

                    "deadline": make_datetime(18),
                    "priority": 1,
                    "difficulty": 3,
                    "focusRequired": 3,
                }
            ],

            "fixedSchedules": [
                {
                    "fixedScheduleId": 4,
                    "title": "종일 수업",
                    "startTime": make_datetime(0),
                    "endTime": make_datetime(17),
                    "locked": True,
                }
            ],

            "existingSchedules": [],
        }

        response = self.client.post(
            "/schedules/generate",
            json=request_body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=f"가용 시간 부족 테스트 요청 실패: {response.text}",
        )

        data = response.json()

      

        self.assertTrue(
            data.get("success"),
            msg=f"API 응답이 실패함: {data}",
        )

        schedules = data.get("schedules", [])

        scheduled_task_ids = {
            str(schedule.get("taskId"))
            for schedule in schedules
            if schedule.get("taskId") is not None
        }

        # 시간 부족 작업은 정상 일정에 생성되면 안 됨
        self.assertNotIn(
            "3",
            scheduled_task_ids,
            msg=(
                "시간이 부족한 taskId=3이 일정에 생성됨: "
                f"{schedules}"
            ),
        )

        unscheduled_tasks = data.get(
            "unscheduledTasks",
            [],
        )

        unscheduled_task = None

        for task in unscheduled_tasks:
            if str(task.get("taskId")) == "3":
                unscheduled_task = task
                break

        self.assertIsNotNone(
            unscheduled_task,
            msg=(
                "시간이 부족한 taskId=3이 "
                f"unscheduledTasks에 없음: {unscheduled_tasks}"
            ),
        )

        # 제목 확인
        self.assertEqual(
            unscheduled_task.get("title"),
            "발표 연습",
            msg=f"작업 제목이 올바르지 않음: {unscheduled_task}",
        )

        # 필요한 시간이 요청값으로 보완됐는지 확인
        self.assertEqual(
            unscheduled_task.get("requiredMinutes"),
            1200,
            msg=f"필요 시간이 올바르지 않음: {unscheduled_task}",
        )

        # 실패 사유 확인
        self.assertEqual(
            unscheduled_task.get("reasonCode"),
            "INSUFFICIENT_TIME",
            msg=f"reasonCode가 올바르지 않음: {unscheduled_task}",
        )

        required_minutes = unscheduled_task.get(
            "requiredMinutes",
            0,
        )
        available_minutes = unscheduled_task.get(
            "availableMinutes",
            0,
        )

        # 가용 시간이 필요 시간보다 작아야 함
        self.assertLess(
            available_minutes,
            required_minutes,
            msg=(
                "가용 시간이 필요 시간보다 적지 않음: "
                f"{unscheduled_task}"
            ),
        )

        warnings = data.get("warnings", [])

        insufficient_warning_exists = any(
            (
                str(
                    warning.get(
                        "taskId",
                        warning.get("task_id"),
                    )
                ) == "3"
                and warning.get("code")
                == "UNSCHEDULABLE_TASK"
            )
            for warning in warnings
        )

        self.assertTrue(
            insufficient_warning_exists,
            msg=f"가용 시간 부족 경고가 없음: {warnings}",
        )


    def test_deadline_passed_returns_unscheduled_task(self):
        """
        마감 시간이 이미 지난 작업이 schedules에 생성되지 않고
        unscheduledTasks에 DEADLINE_PASSED로 포함되는지 테스트한다.
        """

        # 현재 시각보다 2시간 전을 마감 시간으로 설정
        past_deadline = datetime.now() - timedelta(hours=2)
        test_date = past_deadline.date()

        week_start = test_date - timedelta(
            days=test_date.weekday()
        )
        week_end = week_start + timedelta(days=6)

        request_body = {
            "requestId": "deadline-passed-test-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",

            "tasks": [
                {
                    "taskId": 4,
                    "title": "지난 보고서 제출",
                    "estimatedMinutes": 60,
                    "deadline": past_deadline.isoformat(
                        timespec="seconds"
                    ),
                    "priority": 1,
                    "difficulty": 2,
                    "focusRequired": 2,
                }
            ],

            "fixedSchedules": [],
            "existingSchedules": [],
        }

        response = self.client.post(
            "/schedules/generate",
            json=request_body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=f"마감 경과 테스트 요청 실패: {response.text}",
        )

        data = response.json()

      

        self.assertTrue(
            data.get("success"),
            msg=f"API 응답이 실패함: {data}",
        )

        schedules = data.get("schedules", [])

        # taskId=4가 정상 일정에 생성되지 않아야 함
        scheduled_task_ids = {
            str(schedule.get("taskId"))
            for schedule in schedules
            if schedule.get("taskId") is not None
        }

        self.assertNotIn(
            "4",
            scheduled_task_ids,
            msg=(
                "마감이 지난 taskId=4가 일정에 생성됨: "
                f"{schedules}"
            ),
        )

        unscheduled_tasks = data.get(
            "unscheduledTasks",
            [],
        )

        # unscheduledTasks에서 taskId=4 찾기
        unscheduled_task = None

        for task in unscheduled_tasks:
            if str(task.get("taskId")) == "4":
                unscheduled_task = task
                break

        self.assertIsNotNone(
            unscheduled_task,
            msg=(
                "마감이 지난 taskId=4가 "
                f"unscheduledTasks에 없음: {unscheduled_tasks}"
            ),
        )

        # 제목이 요청 작업에서 보완됐는지 확인
        self.assertEqual(
            unscheduled_task.get("title"),
            "지난 보고서 제출",
            msg=f"작업 제목이 올바르지 않음: {unscheduled_task}",
        )

        # 필요한 시간이 요청값 60분인지 확인
        self.assertEqual(
            unscheduled_task.get("requiredMinutes"),
            60,
            msg=f"필요 시간이 올바르지 않음: {unscheduled_task}",
        )

        # 가용 시간은 0분이어야 함
        self.assertEqual(
            unscheduled_task.get("availableMinutes"),
            0,
            msg=f"가용 시간이 올바르지 않음: {unscheduled_task}",
        )

        # 실패 사유가 마감 경과인지 확인
        self.assertEqual(
            unscheduled_task.get("reasonCode"),
            "DEADLINE_PASSED",
            msg=f"reasonCode가 올바르지 않음: {unscheduled_task}",
        )

        warnings = data.get("warnings", [])

        # 원본 경고 코드 확인
        deadline_warning = None

        for warning in warnings:
            warning_task_id = warning.get(
                "taskId",
                warning.get("task_id"),
            )

            if (
                str(warning_task_id) == "4"
                and warning.get("code")
                == "DEADLINE_ALREADY_PASSED"
            ):
                deadline_warning = warning
                break

        self.assertIsNotNone(
            deadline_warning,
            msg=(
                "DEADLINE_ALREADY_PASSED 경고가 없음: "
                f"{warnings}"
            ),
        )

        # 경고 상세 정보에 실제 마감 시간이 있는지 확인
        warning_details = deadline_warning.get(
            "details",
            {},
        )

        self.assertIn(
            "effective_deadline",
            warning_details,
            msg=(
                "경고 details에 effective_deadline이 없음: "
                f"{deadline_warning}"
            ),
        )


if __name__ == "__main__":
    unittest.main()
