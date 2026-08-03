import unittest
from datetime import datetime, timedelta, time

from api_test_base import BaseAPITest


class TestAPIReplan(BaseAPITest):
    def test_replan_api_success(self):
        """
        긴급 고정 일정이 추가되었을 때
        기존 생성 일정이 겹치지 않는 시간으로 이동하는지 테스트한다.
        """

        # 테스트 실행일의 다음 날 사용
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
            "requestId": "replan-test-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",

            # 이 시간 이후의 일정을 다시 배치
            "replanFromTime": make_datetime(9),

            "tasks": [
                {
                    "taskId": 1,
                    "title": "발표 자료 조사",
                    "estimatedMinutes": 60,
                    "deadline": make_datetime(18),
                    "priority": 1,
                    "difficulty": 2,
                    "focusRequired": 2,
                }
            ],

            # 새로 추가된 긴급 일정
            "fixedSchedules": [
                {
                    "fixedScheduleId": 2,
                    "title": "긴급 병원 예약",
                    "startTime": make_datetime(9),
                    "endTime": make_datetime(11),
                    "locked": True,
                }
            ],

            # 재배치 전 기존 AI 일정
            "existingSchedules": [
                {
                    "blockId": "generated:1:step-1",
                    "taskId": "1",
                    "title": "발표 자료 조사",
                    "stepOrder": 1,
                    "startTime": make_datetime(9),
                    "endTime": make_datetime(10),
                    "source": "GENERATED",
                    "locked": False,
                    "reason": "기존 생성 일정",
                }
            ],      
        }

        response = self.client.post(
            "/ai/schedules/replan",
            json=request_body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=f"replan API 호출 실패: {response.text}",
        )

        data = response.json()

        

        self.assertIn(
            "success",
            data,
            msg=f"success 필드가 없음: {data}",
        )

        self.assertTrue(
            data["success"],
            msg=f"재배치 실패 응답: {data}",
        )

        self.assertIn(
            "schedules",
            data,
            msg=f"schedules 필드가 없음: {data}",
        )

        schedules = data["schedules"]

        self.assertIsInstance(
            schedules,
            list,
            msg="schedules가 리스트가 아님",
        )

        self.assertGreater(
            len(schedules),
            0,
            msg=f"재배치된 일정이 없음. 전체 응답: {data}",
        )

        # taskId=1인 재배치 결과 찾기
        replanned_task = None

        for schedule in schedules:
            if str(schedule.get("taskId")) == "1":
                replanned_task = schedule
                break

        self.assertIsNotNone(
            replanned_task,
            msg=f"taskId=1 재배치 결과가 없음: {schedules}",
        )

        self.assertIn("startTime", replanned_task)
        self.assertIn("endTime", replanned_task)

        replanned_start = datetime.fromisoformat(
            replanned_task["startTime"]
        )
        replanned_end = datetime.fromisoformat(
            replanned_task["endTime"]
        )

        urgent_start = datetime.fromisoformat(
            make_datetime(9)
        )
        urgent_end = datetime.fromisoformat(
            make_datetime(11)
        )

        # 병원 예약 09:00~11:00과 겹치는지 검사
        overlaps = (
            replanned_start < urgent_end
            and replanned_end > urgent_start
        )

        self.assertFalse(
            overlaps,
            msg=(
                "재배치된 일정이 긴급 병원 예약과 겹침: "
                f"{replanned_task}"
            ),
        )

        # 기존 09:00 일정이 11:00 이후로 이동했는지 검사
        self.assertGreaterEqual(
            replanned_start,
            urgent_end,
            msg=(
                "기존 일정이 병원 예약 이후로 이동하지 않음: "
                f"{replanned_task}"
            ),
        )

        # 변경 기록에 MOVED가 포함됐는지 확인
        changes = data.get("changes", [])

        moved_exists = any(
            change.get("action") == "MOVED"
            for change in changes
        )

        self.assertTrue(
            moved_exists,
            msg=f"changes에 MOVED가 없음: {changes}",
        )


    def test_locked_schedule_is_preserved(self):
        """
        locked=true인 기존 일정이 재배치 후에도
        같은 시간으로 유지되는지 테스트한다.
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
            "requestId": "locked-test-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",
            "replanFromTime": make_datetime(9),

            "tasks": [
                {
                    "taskId": 1,
                    "title": "발표 자료 조사",
                    "estimatedMinutes": 60,
                    "deadline": make_datetime(18),
                    "priority": 1,
                    "difficulty": 2,
                    "focusRequired": 2,
                }
            ],

            "fixedSchedules": [],

            "existingSchedules": [
                {
                    "blockId": "generated:1:step-1",
                    "taskId": "1",
                    "title": "발표 자료 조사",
                    "stepOrder": 1,
                    "startTime": make_datetime(9),
                    "endTime": make_datetime(10),
                    "source": "GENERATED",
                    "locked": True,
                    "reason": "사용자가 고정한 일정",
                }
            ],
        }

        response = self.client.post(
            "/ai/schedules/replan",
            json=request_body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=f"locked 일정 재배치 요청 실패: {response.text}",
        )

        data = response.json()

  

        self.assertTrue(
            data.get("success"),
            msg=f"재배치 응답이 실패함: {data}",
        )

        preserved_schedules = data.get(
            "preservedSchedules",
            [],
        )

        locked_schedule = None

        for schedule in preserved_schedules:
            if schedule.get("blockId") == "generated:1:step-1":
                locked_schedule = schedule
                break

        self.assertIsNotNone(
            locked_schedule,
            msg=(
                "locked 일정이 preservedSchedules에 없음: "
                f"{preserved_schedules}"
            ),
        )

        # 시작 시간이 그대로인지 확인
        self.assertEqual(
            locked_schedule.get("startTime"),
            make_datetime(9),
            msg=f"locked 일정 시작 시간이 변경됨: {locked_schedule}",
        )

        # 종료 시간이 그대로인지 확인
        self.assertEqual(
            locked_schedule.get("endTime"),
            make_datetime(10),
            msg=f"locked 일정 종료 시간이 변경됨: {locked_schedule}",
        )

        # locked 값이 계속 true인지 확인
        self.assertTrue(
            locked_schedule.get("locked"),
            msg=f"locked 값이 유지되지 않음: {locked_schedule}",
        )

        # 최종 일정에도 존재하는지 확인
        final_schedules = data.get("finalSchedules", [])

        exists_in_final = any(
            schedule.get("blockId") == "generated:1:step-1"
            for schedule in final_schedules
        )

        self.assertTrue(
            exists_in_final,
            msg=f"locked 일정이 finalSchedules에 없음: {final_schedules}",
        )

        # locked 일정이 이동 또는 삭제 처리되지 않았는지 확인
        changes = data.get("changes", [])

        invalid_changes = [
            change
            for change in changes
            if (
                change.get("blockId") == "generated:1:step-1"
                and change.get("action") in {"MOVED", "REMOVED"}
            )
        ]

        self.assertEqual(
            invalid_changes,
            [],
            msg=(
                "locked 일정이 이동 또는 삭제 처리됨: "
                f"{invalid_changes}"
            ),
        )


    def test_completed_task_is_excluded(self):
        """
        completed=true인 작업이 재배치 결과에
        다시 생성되지 않는지 테스트한다.
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
            "requestId": "completed-test-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",
            "replanFromTime": make_datetime(8),

            # taskId=1은 완료한 작업
            "completedTaskIds": [1],
            "postponedTaskIds": [],
            
            "tasks": [
                {
                    "taskId": 1,
                    "title": "완료한 발표 자료 조사",
                    "estimatedMinutes": 60,
                    "deadline": make_datetime(18),
                    "priority": 1,
                    "difficulty": 2,
                    "focusRequired": 2,
                },
                {
                    "taskId": 2,
                    "title": "남은 발표 연습",
                    "estimatedMinutes": 60,
                    "deadline": make_datetime(19),
                    "priority": 2,
                    "difficulty": 2,
                    "focusRequired": 2,
                },
            ],

            "fixedSchedules": [],

            "existingSchedules": [
                {
                    "blockId": "generated:1:step-1",
                    "taskId": "1",
                    "title": "완료한 발표 자료 조사",
                    "stepOrder": 1,
                    "startTime": make_datetime(9),
                    "endTime": make_datetime(10),
                    "source": "GENERATED",
                    "locked": False,
                    "completed": True,
                    "reason": "사용자가 작업을 완료함",
                }
            ],
        }

        response = self.client.post(
            "/ai/schedules/replan",
            json=request_body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=f"완료 작업 제외 요청 실패: {response.text}",
        )

        data = response.json()

   

        self.assertTrue(
            data.get("success"),
            msg=f"재배치 응답이 실패함: {data}",
        )

        schedules = data.get("schedules", [])

        # 새로 생성된 일정의 taskId 목록
        scheduled_task_ids = {
            str(schedule.get("taskId"))
            for schedule in schedules
            if schedule.get("taskId") is not None
        }

        # 완료한 taskId=1은 다시 생성되면 안 됨
        self.assertNotIn(
            "1",
            scheduled_task_ids,
            msg=(
                "완료한 taskId=1이 다시 일정에 배치됨: "
                f"{schedules}"
            ),
        )

        # 완료하지 않은 taskId=2는 생성돼야 함
        self.assertIn(
            "2",
            scheduled_task_ids,
            msg=(
                "완료하지 않은 taskId=2가 배치되지 않음: "
                f"{schedules}"
            ),
        )

        preserved_schedules = data.get(
            "preservedSchedules",
            [],
        )

        preserved_task_ids = {
            str(schedule.get("taskId"))
            for schedule in preserved_schedules
            if schedule.get("taskId") is not None
        }

        # 완료 일정이 preservedSchedules에 남지 않는지 확인
        self.assertNotIn(
            "1",
            preserved_task_ids,
            msg=(
                "완료한 taskId=1이 preservedSchedules에 남아 있음: "
                f"{preserved_schedules}"
            ),
        )


    def test_postponed_task_with_urgent_schedule(self):
        """
        미룬 작업과 긴급 일정이 함께 전달됐을 때
        기존 작업이 긴급 일정 이후로 재배치되는지 테스트한다.
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
            "requestId": "postpone-urgent-test-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",
            "replanFromTime": make_datetime(9),

            # 완료 작업은 없음
            "completedTaskIds": [],

            # taskId=1을 미룬 작업으로 전달
            "postponedTaskIds": [1],

            "tasks": [
                {
                    "taskId": 1,
                    "title": "미룬 발표 자료 조사",
                    "estimatedMinutes": 60,
                    "deadline": make_datetime(18),
                    "priority": 1,
                    "difficulty": 2,
                    "focusRequired": 2,
                }
            ],

            # 새로 추가된 긴급 일정
            "fixedSchedules": [
                {
                    "fixedScheduleId": 3,
                    "title": "긴급 병원 예약",
                    "startTime": make_datetime(9),
                    "endTime": make_datetime(11),
                    "locked": True,
                }
            ],

            # 재배치 전 기존 일정
            "existingSchedules": [
                {
                    "blockId": "generated:1:step-1",
                    "taskId": "1",
                    "title": "미룬 발표 자료 조사",
                    "stepOrder": 1,
                    "startTime": make_datetime(9),
                    "endTime": make_datetime(10),
                    "source": "GENERATED",
                    "locked": False,
                    "completed": False,
                    "reason": "기존 생성 일정",
                }
            ],
        }

        response = self.client.post(
            "/ai/schedules/replan",
            json=request_body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=f"미루기·긴급 일정 요청 실패: {response.text}",
        )

        data = response.json()

       

        self.assertTrue(
            data.get("success"),
            msg=f"재배치 응답이 실패함: {data}",
        )

        schedules = data.get("schedules", [])

        # taskId=1의 재배치 결과 찾기
        postponed_task = None

        for schedule in schedules:
            if str(schedule.get("taskId")) == "1":
                postponed_task = schedule
                break

        self.assertIsNotNone(
            postponed_task,
            msg=f"미룬 taskId=1이 재배치되지 않음: {schedules}",
        )

        postponed_start = datetime.fromisoformat(
            postponed_task["startTime"]
        )
        postponed_end = datetime.fromisoformat(
            postponed_task["endTime"]
        )

        urgent_start = datetime.fromisoformat(
            make_datetime(9)
        )
        urgent_end = datetime.fromisoformat(
            make_datetime(11)
        )

        # 긴급 일정과 시간이 겹치지 않는지 확인
        overlaps = (
            postponed_start < urgent_end
            and postponed_end > urgent_start
        )

        self.assertFalse(
            overlaps,
            msg=(
                "미룬 작업이 긴급 일정과 겹침: "
                f"{postponed_task}"
            ),
        )

        # 긴급 일정 종료 후에 배치됐는지 확인
        self.assertGreaterEqual(
            postponed_start,
            urgent_end,
            msg=(
                "미룬 작업이 긴급 일정 이후로 이동하지 않음: "
                f"{postponed_task}"
            ),
        )

        # 미루기 점수가 반영됐는지 확인
        scores = data.get("scores", {})
        task_score = scores.get("1", {})

        self.assertGreater(
            task_score.get("postponement", 0),
            0,
            msg=f"미루기 점수가 반영되지 않음: {scores}",
        )

        # 기존 일정이 MOVED로 기록됐는지 확인
        changes = data.get("changes", [])

        moved_exists = any(
            str(change.get("taskId")) == "1"
            and change.get("action") == "MOVED"
            for change in changes
        )

        self.assertTrue(
            moved_exists,
            msg=f"taskId=1의 MOVED 기록이 없음: {changes}",
        )

        # 긴급 일정이 preservedSchedules에 유지되는지 확인
        preserved_schedules = data.get(
            "preservedSchedules",
            [],
        )

        urgent_preserved = any(
            schedule.get("blockId") == "fixed:3"
            and schedule.get("locked") is True
            for schedule in preserved_schedules
        )

        self.assertTrue(
            urgent_preserved,
            msg=(
                "긴급 일정이 preservedSchedules에 유지되지 않음: "
                f"{preserved_schedules}"
            ),
        )


if __name__ == "__main__":
    unittest.main()
