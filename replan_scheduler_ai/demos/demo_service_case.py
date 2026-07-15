from datetime import datetime, time
from pprint import pprint

from ai_scheduler import (
    CalendarBlock,
    SchedulePreferences,
    Task,
    schedule_tasks,
)


# 사용자가 앱을 실행한 현재 시각이라고 가정
# 사진 기준으로 7월 20일 아침부터 23일까지의 일정을 자동 배치해 보는 예시
now = datetime(2026, 7, 20, 8, 0)


# 사용자 기본 설정
# 하루 중 자동 배치 가능한 시간: 09:00~22:00
# 집중이 잘 되는 시간: 09:00~12:00
preferences = SchedulePreferences(
    day_start=time(9, 0),
    day_end=time(22, 0),
    focus_start=time(9, 0),
    focus_end=time(12, 0),
)


# 사용자가 입력한 할 일 목록
tasks = [
    Task(
        id="python-week1-review",
        title="파이썬 1주차 개념 복습",
        deadline=datetime(2026, 7, 22, 12, 0),
        lecture_start=datetime(2026, 7, 22, 12, 0),
        estimated_minutes=90,
        priority=5,
        difficulty=4,
        focus_required=5,
        category="복습",
        course_id="python",
        week_order=1,
        task_type="review",
    ),
    Task(
        id="python-week1-coding",
        title="파이썬 1주차 코드 짜기",
        deadline=datetime(2026, 7, 22, 12, 0),
        lecture_start=datetime(2026, 7, 22, 12, 0),
        estimated_minutes=180,
        priority=5,
        difficulty=5,
        focus_required=5,
        category="개발",
        course_id="python",
        week_order=1,
        task_type="practice",
        prerequisite_ids=["python-week1-review"],
        max_block_minutes=90,
    ),
    Task(
        id="ai-service-case-plan",
        title="AI 서비스 사례 다음 개발 계획 정리",
        deadline=datetime(2026, 7, 22, 23, 0),
        estimated_minutes=90,
        priority=4,
        difficulty=4,
        focus_required=4,
        category="경진대회",
    ),
    Task(
        id="week10-lecture",
        title="10주차 강의 듣기",
        deadline=datetime(2026, 7, 23, 12, 0),
        estimated_minutes=90,
        priority=4,
        difficulty=3,
        focus_required=4,
        category="강의",
        course_id="major",
        week_order=10,
    ),
    Task(
        id="week10-summary",
        title="10주차 강의 정리",
        deadline=datetime(2026, 7, 23, 16, 0),
        estimated_minutes=60,
        priority=4,
        difficulty=3,
        focus_required=4,
        category="정리",
        course_id="major",
        week_order=10,
        prerequisite_ids=["week10-lecture"],
    ),
    Task(
        id="ux-hackathon-select",
        title="UX 해커톤 선정 및 아이디어 정리",
        deadline=datetime(2026, 7, 23, 17, 0),
        estimated_minutes=60,
        priority=3,
        difficulty=3,
        focus_required=3,
        category="프로젝트",
    ),
    Task(
        id="python-extra-practice",
        title="파이썬 실습 문제 추가 풀이",
        deadline=datetime(2026, 7, 23, 23, 0),
        estimated_minutes=120,
        priority=3,
        difficulty=4,
        focus_required=4,
        category="실습",
        course_id="python",
        week_order=1,
        prerequisite_ids=["python-week1-review"],
        max_block_minutes=60,
        postpone_count=1,
    ),
]


# 이미 등록되어 있는 고정 일정
# 사진 기준으로 확실히 보이는 일정:
# 7/22 파이썬 수업 12:00~15:00
# 7/23 알바 17:00~22:00
fixed_blocks = [
    CalendarBlock(
        block_id="python-class",
        title="파이썬 수업",
        start=datetime(2026, 7, 22, 12, 0),
        end=datetime(2026, 7, 22, 15, 0),
        source="fixed",
        locked=True,
    ),
    CalendarBlock(
        block_id="part-time-job",
        title="알바",
        start=datetime(2026, 7, 23, 17, 0),
        end=datetime(2026, 7, 23, 22, 0),
        source="fixed",
        locked=True,
    ),
]


# 스케줄링 알고리즘 실행
result = schedule_tasks(tasks, fixed_blocks, preferences, now)

# 결과 출력
pprint(result.to_dict(), sort_dicts=False)