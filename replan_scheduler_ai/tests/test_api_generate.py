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

    def test_ai_decomposition_3_5_7(self):

        from datetime import (
            datetime,
            timedelta,
            time,
        )

        test_date = datetime.now().date()

        week_start = (
            test_date
            - timedelta(
                days=test_date.weekday()
            )
        )

        week_end = (
            week_start
            + timedelta(days=6)
        )

        for desired_steps in [3, 5, 7]:

            with self.subTest(
                desired_steps=desired_steps
            ):

                body = {
                    "requestId": (
                        f"decompose-test-"
                        f"{desired_steps}"
                    ),

                    "userId": 1,

                    "weekStartDate": (
                        week_start.isoformat()
                    ),

                    "weekEndDate": (
                        week_end.isoformat()
                    ),

                    "timezone": "Asia/Seoul",

                    "tasks": [
                        {
                            "taskId": 100,
                            "title": (
                                "경진대회 발표 준비"
                            ),

                            "estimatedMinutes": (
                                desired_steps * 30
                            ),

                            "deadline": (
                                datetime.combine(
                                    test_date,
                                    time(21, 0),
                                ).isoformat()
                            ),

                            "priority": 5,
                            "difficulty": 3,
                            "focusRequired": 4,

                            "useAiDecomposition": True,
                            "desiredSteps": (
                                desired_steps
                            ),

                            "postponeCount": 0,
                            "completedMinutes": 0,
                            "completed": False,
                            "prerequisiteTaskIds": [],
                        }
                    ],

                    "fixedSchedules": [],
                    "existingSchedules": [],
                }

                response = self.client.post(
                    "/schedules/generate",
                    json=body,
                )

                self.assertEqual(
                    response.status_code,
                    200,
                    msg=response.text,
                )

                data = response.json()

                schedules = [
                    schedule
                    for schedule
                    in data.get(
                        "schedules",
                        [],
                    )
                    if str(
                        schedule.get("taskId")
                    ) == "100"
                ]

                print(
                    f"\n=== {desired_steps}단계 ==="
                )

                for schedule in schedules:
                    print(
                        schedule["stepOrder"],
                        schedule["title"],
                        schedule["startTime"],
                        "~",
                        schedule["endTime"],
                    )

                self.assertEqual(
                    len(schedules),
                    desired_steps,
                    msg=data,
                )

                step_orders = sorted(
                    schedule["stepOrder"]
                    for schedule in schedules
                )

                self.assertEqual(
                    step_orders,
                    list(
                        range(
                            1,
                            desired_steps + 1,
                        )
                    ),
                )

                self.assertTrue(
                    all(
                        str(
                            schedule["blockId"]
                        ).startswith(
                            "generated:100:step-"
                        )
                        for schedule in schedules
                    )
                )

        actual_orders = [
            schedule["stepOrder"]
            for schedule in schedules
        ]

        self.assertEqual(
            actual_orders,
            list(range(1, desired_steps + 1)),
        )
        
    def test_ai_decomposition_can_scatter_across_gaps(self):

        from datetime import datetime, timedelta, time

        test_date = datetime.now().date()

        week_start = (
            test_date
            - timedelta(days=test_date.weekday())
        )

        week_end = week_start + timedelta(days=6)

        body = {
            "requestId": "decompose-scatter-test-001",
            "userId": 1,

            "weekStartDate": week_start.isoformat(),
            "weekEndDate": week_end.isoformat(),
            "timezone": "Asia/Seoul",

            "tasks": [
                {
                    "taskId": 200,
                    "title": "경진대회 발표 준비",
                    "estimatedMinutes": 150,

                    "deadline": datetime.combine(
                        test_date,
                        time(20, 0),
                    ).isoformat(),

                    "priority": 5,
                    "difficulty": 3,
                    "focusRequired": 4,

                    "useAiDecomposition": True,
                    "desiredSteps": 5,

                    "postponeCount": 0,
                    "completedMinutes": 0,
                    "completed": False,
                    "prerequisiteTaskIds": [],
                }
            ],

            # 일부러 빈칸을 여러 군데 만든다.
            "fixedSchedules": [
                {
                    "fixedScheduleId": 101,
                    "title": "오전 고정 일정",

                    "startTime": datetime.combine(
                        test_date,
                        time(10, 0),
                    ).isoformat(),

                    "endTime": datetime.combine(
                        test_date,
                        time(12, 0),
                    ).isoformat(),
                },

                {
                    "fixedScheduleId": 102,
                    "title": "오후 고정 일정",

                    "startTime": datetime.combine(
                        test_date,
                        time(13, 0),
                    ).isoformat(),

                    "endTime": datetime.combine(
                        test_date,
                        time(15, 0),
                    ).isoformat(),
                },
            ],

            "existingSchedules": [],
        }

        response = self.client.post(
            "/schedules/generate",
            json=body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=response.text,
        )

        data = response.json()

        print(
            "\nPRESERVED SCHEDULES:",
            data.get("preservedSchedules", [])
        )

        print(
            "DUPLICATE REQUEST:",
            data.get("duplicateRequest")
        )

        schedules = [
            schedule
            for schedule in data.get("schedules", [])
            if str(schedule.get("taskId")) == "200"
        ]

        schedules.sort(
            key=lambda item: item["startTime"]
        )

        print("\n=== 쪼개기 자유 배치 테스트 ===")

        for schedule in schedules:
            print(
                "STEP",
                schedule["stepOrder"],
                "|",
                schedule["title"],
                "|",
                schedule["startTime"],
                "~",
                schedule["endTime"],
            )

        self.assertEqual(
            len(schedules),
            5,
            msg=data,
        )

        # 고정 일정 앞쪽 빈칸을 사용했는지
        fixed1_start = datetime.combine(
            test_date,
            time(10, 0),
        )

        used_before_fixed = any(
            datetime.fromisoformat(
                schedule["endTime"]
            ) <= fixed1_start
            for schedule in schedules
        )

        # 첫 번째 고정 일정과 두 번째 고정 일정 사이
        # 12:00~13:00 공간을 사용했는지
        gap_start = datetime.combine(
            test_date,
            time(12, 0),
        )

        gap_end = datetime.combine(
            test_date,
            time(13, 0),
        )

        used_middle_gap = any(
            datetime.fromisoformat(
                schedule["startTime"]
            ) >= gap_start
            and
            datetime.fromisoformat(
                schedule["endTime"]
            ) <= gap_end
            for schedule in schedules
        )

        # 서로 붙어만 있지 않고 실제 시간 간격이 있는지
        has_scattered_gap = False

        for previous, current in zip(
            schedules,
            schedules[1:],
        ):
            previous_end = datetime.fromisoformat(
                previous["endTime"]
            )

            current_start = datetime.fromisoformat(
                current["startTime"]
            )

            if current_start > previous_end:
                has_scattered_gap = True
                break

        self.assertTrue(
            used_before_fixed,
            msg=(
                "고정 일정 전 빈 공간을 사용하지 않았습니다. "
                f"{schedules}"
            ),
        )

        self.assertTrue(
            used_middle_gap,
            msg=(
                "고정 일정 사이 빈 공간을 사용하지 않았습니다. "
                f"{schedules}"
            ),
        )

        self.assertTrue(
            has_scattered_gap,
            msg=(
                "분해된 단계가 모두 한 덩어리로 "
                f"붙어서 배치되었습니다: {schedules}"
            ),
        )




    def test_generate_starts_from_requested_week(self):

        from datetime import (
            datetime,
            timedelta,
            time,
        )

        today = datetime.now().date()

        # 무조건 "다음 주 월요일"을 구함
        days_until_next_monday = (
            7 - today.weekday()
        )

        week_start = (
            today
            + timedelta(
                days=days_until_next_monday
            )
        )

        week_end = (
            week_start
            + timedelta(days=6)
        )

        body = {
            "requestId": "future-week-test-001",
            "userId": 1,

            "weekStartDate": (
                week_start.isoformat()
            ),

            "weekEndDate": (
                week_end.isoformat()
            ),

            "timezone": "Asia/Seoul",

            "tasks": [
                {
                    "taskId": 999,
                    "title": "다음 주 테스트 작업",

                    "estimatedMinutes": 120,

                    "deadline": (
                        datetime.combine(
                            week_end,
                            time(20, 0),
                        ).isoformat()
                    ),

                    "priority": 3,
                    "difficulty": 3,
                    "focusRequired": 3,

                    # Gemini 테스트가 아니라
                    # 날짜 테스트이므로 분해 OFF
                    "useAiDecomposition": False,
                    "desiredSteps": None,

                    "postponeCount": 0,
                    "completedMinutes": 0,
                    "completed": False,

                    "prerequisiteTaskIds": [],
                }
            ],

            "fixedSchedules": [],
            "existingSchedules": [],
        }

        response = self.client.post(
            "/schedules/generate",
            json=body,
        )

        self.assertEqual(
            response.status_code,
            200,
            msg=response.text,
        )

        data = response.json()

        schedules = data.get(
            "schedules",
            []
        )

        print(
            "\n=== weekStartDate 테스트 ==="
        )

        print(
            "요청 주간:",
            week_start,
            "~",
            week_end,
        )

        for schedule in schedules:
            print(
                schedule["title"],
                "|",
                schedule["startTime"],
                "~",
                schedule["endTime"],
            )

        self.assertGreater(
            len(schedules),
            0,
            msg=data,
        )

        requested_start = datetime.combine(
            week_start,
            time(9, 0),
        )

        requested_end = datetime.combine(
            week_end,
            time(22, 0),
        )

        for schedule in schedules:

            start_time = datetime.fromisoformat(
                schedule["startTime"]
            )

            end_time = datetime.fromisoformat(
                schedule["endTime"]
            )

            # 요청한 주보다 이전이면 실패
            self.assertGreaterEqual(
                start_time,
                requested_start,
                msg=(
                    "weekStartDate보다 이전에 "
                    f"일정이 생성됐습니다: {schedule}"
                ),
            )

            # 요청한 주를 넘어가도 실패
            self.assertLessEqual(
                end_time,
                requested_end,
                msg=(
                    "weekEndDate보다 이후에 "
                    f"일정이 생성됐습니다: {schedule}"
                ),
            )
if __name__ == "__main__":
    unittest.main()
