import unittest
from datetime import datetime, timedelta, time

from api_test_base import BaseAPITest


class TestAPIGenerate(BaseAPITest):
    def test_generate_api_success(self):
        """
        generate API가 정상적으로 일정 생성 응답을 반환하는지 테스트한다.
        """

        # 테스트 실행일의 다음 날을 일정 생성 날짜로 사용
        test_date = (datetime.now() + timedelta(days=1)).date()

        # 해당 날짜가 포함된 주의 월요일과 일요일 계산
        week_start = test_date - timedelta(days=test_date.weekday())
        week_end = week_start + timedelta(days=6)

        def make_datetime(hour: int, minute: int = 0) -> str:
            return datetime.combine(
                test_date,
                time(hour=hour, minute=minute),
            ).isoformat()

        request_body = {
            "requestId": "generate-test-001",
            "userId": 1,
            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",
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
            "fixedSchedules": [
                {
                    "fixedScheduleId": 1,
                    "title": "수업",
                    "startTime": make_datetime(10),
                    "endTime": make_datetime(12),
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
            msg=f"generate API 호출 실패: {response.text}",
        )

        data = response.json()

        # 결과 확인용 출력
  

        self.assertIn("success", data)

        self.assertTrue(
            data["success"],
            msg=f"응답 success=false: {data}",
        )

        self.assertIn(
            "schedules",
            data,
            msg=f"schedules 필드 없음: {data}",
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
            msg=f"생성된 일정이 없음. 전체 응답: {data}",
        )

        # taskId=1인 일정 찾기
        generated_task = None

        for schedule in schedules:
            if str(schedule.get("taskId")) == "1":
                generated_task = schedule
                break

        self.assertIsNotNone(
            generated_task,
            msg=f"taskId=1 일정이 생성되지 않음: {schedules}",
        )

        # 시간 필드 존재 확인
        self.assertIn("startTime", generated_task)
        self.assertIn("endTime", generated_task)

        start_time = datetime.fromisoformat(
            generated_task["startTime"]
        )

        end_time = datetime.fromisoformat(
            generated_task["endTime"]
        )

        # 60분 배치됐는지 확인
        duration_minutes = int(
            (end_time - start_time).total_seconds() / 60
        )

        self.assertEqual(
            duration_minutes,
            60,
            msg=f"배치 시간이 60분이 아님: {duration_minutes}분",
        )

        # 고정 일정 10:00~12:00과 겹치지 않는지 확인
        fixed_start = datetime.fromisoformat(make_datetime(10))
        fixed_end = datetime.fromisoformat(make_datetime(12))

        overlaps = (
            start_time < fixed_end
            and end_time > fixed_start
        )

        self.assertFalse(
            overlaps,
            msg=f"생성 일정이 고정 일정과 겹침: {generated_task}",
        )


if __name__ == "__main__":
    unittest.main()
