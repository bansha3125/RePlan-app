# AI 자동 일정 관리 모듈

이 폴더는 프론트엔드나 데이터베이스가 아니라 **AI·알고리즘 담당 코드만** 포함한다.

## 구성

```text
ai_scheduler_project/
├─ ai_scheduler/
│  ├─ models.py           # 작업·일정·결과 데이터 구조
│  ├─ scheduler.py        # 자체 스케줄링 및 재배치 알고리즘
│  ├─ gemini_service.py   # Gemini 작업 분해·설명·피드백
│  ├─ analytics.py        # 수행률·미루기·시간 보정 분석
│  └─ integration.py      # 백엔드가 JSON으로 호출하는 연결 함수
├─ tests/
│  ├─ test_scheduler.py
│  └─ test_analytics.py
├─ demo.py
└─ requirements.txt
```

## 구현된 스케줄링 기능

- 고정 일정을 제외한 빈 시간 계산
- 마감 임박도 계산
- 사용자 우선순위 점수 반영
- 난이도와 집중 필요도 반영
- 선행 작업 순서 검사
- 같은 강좌의 주차별 작업 순서 적용
- 긴 작업 여러 구간 분할
- 강의 전 복습 일정 배치
- 긴급 일정 추가 후 전체 재배치
- 미루기 횟수 증가 후 재배치
- 수동 고정 일정 유지 및 나머지만 재배치
- 배치 불가능 작업 경고

### 점수 구조

```text
최종 점수
= 마감 임박 점수
+ 사용자 우선순위 점수
+ 미루기 횟수 점수
+ 선행 작업 점수
+ 집중 필요도·난이도 점수
```

## 구현된 Gemini 기능

- 큰 작업 자동 분해
- 사용자가 선택한 단계 수에 정확히 맞춰 분해
- 일정 추천 이유 생성
- 반복 미루기 안내 문구 생성
- 사용자 맞춤형 시간 관리 피드백 생성
- API 키 누락, SDK 누락, 요청 실패 시 기본 문구 자동 반환

Gemini는 **자연어 생성만 담당**하고, 실제 일정 배치는 자체 알고리즘이 담당한다.

## 구현된 데이터 분석 기능

- 반복 미루기 감지
- 카테고리별 실제 소요 시간 평균
- 실제/예상 시간 비율에 따른 예상 시간 자동 보정
- 일정 수행률 계산
- 활동 시간 내 유휴 공백 계산
- 미이행 요일·시간·카테고리 패턴 분석
- Gemini 피드백용 분석 데이터 생성

## 테스트 실행

프로젝트 폴더에서 다음 명령어를 실행한다.

```bash
python -m unittest discover -s tests -v
```

## 데모 실행

```bash
python demo.py
```

## Gemini 설치 및 설정

```bash
pip install -r requirements.txt
```

환경 변수에 API 키를 넣는다.

Windows PowerShell:

```powershell
$env:GEMINI_API_KEY="발급받은_API_KEY"
$env:GEMINI_MODEL="gemini-3.5-flash"
```

Gemini API를 연결하지 않아도 스케줄링 알고리즘과 분석 기능은 정상 동작하며, Gemini 함수는 기본 결과를 반환한다.

## 백엔드 연결

백엔드는 `schedule_from_payload()`에 JSON 형태의 딕셔너리를 전달하면 된다.

```python
from ai_scheduler.integration import schedule_from_payload

result = schedule_from_payload(payload)
```

### 입력 예시

```python
payload = {
    "now": "2026-07-01T08:00",
    "tasks": [
        {
            "id": "task-1",
            "title": "영어회화 13주차 복습",
            "deadline": "2026-07-07T14:00",
            "lecture_start": "2026-07-07T14:00",
            "estimated_minutes": 60,
            "priority": 4,
            "difficulty": 3,
            "focus_required": 4,
            "course_id": "english",
            "week_order": 13,
            "task_type": "review"
        }
    ],
    "existing_blocks": [
        {
            "block_id": "class-1",
            "title": "수업",
            "start": "2026-07-01T10:00",
            "end": "2026-07-01T12:00",
            "source": "fixed",
            "locked": True
        }
    ],
    "preferences": {
        "day_start": "09:00",
        "day_end": "22:00",
        "slot_minutes": 30,
        "focus_start": "09:00",
        "focus_end": "12:00"
    }
}
```

출력에는 자동 생성 일정, 유지된 고정 일정, 작업별 점수, 배치 실패 경고가 포함된다.
