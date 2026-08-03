# RePlan AI Scheduler API 명세서

## 1. API 기본 정보

```text
서비스명: RePlan AI Scheduler
기본 주소: http://127.0.0.1:8000
Content-Type: application/json
기본 시간대: Asia/Seoul
날짜 형식: YYYY-MM-DD
시간 형식: YYYY-MM-DDTHH:MM:SS
```

Swagger 문서:

```text
http://127.0.0.1:8000/docs
```

---

## 2. 공통 ID 규칙

### taskId

현재 AI 서버는 다음 두 형식을 모두 허용한다.

```json
{
  "taskId": 1
}
```

```json
{
  "taskId": "task-101"
}
```

AI 서버 내부에서는 비교 오류를 방지하기 위해 `taskId`를 문자열로 변환하여 처리한다.

응답의 `taskId`도 문자열로 반환될 수 있다.

```json
{
  "taskId": "1"
}
```

백엔드에서는 다음 방식으로 비교하는 것을 권장한다.

```java
String.valueOf(taskId)
```

### blockId

AI가 생성한 일정은 다음 형식을 사용한다.

```text
generated:{taskId}:step-{stepOrder}
```

예시:

```text
generated:1:step-1
generated:1:step-2
generated:task-101:step-1
```

고정 일정은 다음 형식을 사용한다.

```text
fixed:{fixedScheduleId}
```

예시:

```text
fixed:10
```

---

# 3. 서버 상태 확인

## GET `/health`

AI 서버가 정상 실행 중인지 확인한다.

같은 기능을 제공하는 경로:

```text
GET /
GET /health
```

### 정상 응답

```json
{
  "success": true,
  "status": "UP",
  "service": "replan-ai",
  "message": "AI Scheduler API is running"
}
```

---

# 4. 최초 일정 생성

## POST `/schedules/generate`

같은 기능을 제공하는 별칭 경로:

```text
POST /ai/schedules/generate
```

사용자의 할 일과 고정 일정을 전달받아 최초 일정을 생성한다.

## 요청 형식

```json
{
  "requestId": "generate-request-001",
  "userId": 1,
  "weekStartDate": "2026-08-03",
  "weekEndDate": "2026-08-09",
  "timezone": "Asia/Seoul",
  "tasks": [],
  "fixedSchedules": [],
  "existingSchedules": []
}
```

## 요청 필드

| 필드 | 자료형 | 필수 | 설명 |
|---|---:|---:|---|
| requestId | string | O | 요청을 구분하는 고유 ID |
| userId | integer | O | 사용자 ID |
| weekStartDate | date | O | 조회 주 시작 날짜 |
| weekEndDate | date | O | 조회 주 종료 날짜 |
| timezone | string | X | 기본값 `Asia/Seoul` |
| tasks | array | X | 배치할 작업 목록 |
| fixedSchedules | array | X | 수업, 알바, 병원 등 고정 일정 |
| existingSchedules | array | X | 기존 일정 목록 |

## tasks 필드

```json
{
  "taskId": 1,
  "title": "발표 자료 조사",
  "estimatedMinutes": 60,
  "deadline": "2026-08-05T18:00:00",
  "priority": 2,
  "difficulty": 2,
  "focusRequired": 3,
  "postponeCount": 0,
  "completedMinutes": 0,
  "remainingMinutes": null,
  "completed": false,
  "prerequisiteTaskIds": []
}
```

| 필드 | 자료형 | 필수 | 설명 |
|---|---:|---:|---|
| taskId | integer 또는 string | O | 원본 작업 ID |
| title | string | O | 작업 이름 |
| estimatedMinutes | integer | O | 예상 소요 시간 |
| deadline | datetime | O | 작업 마감 시각 |
| priority | integer | O | 중요도 |
| difficulty | integer | X | 작업 난이도 |
| focusRequired | integer | X | 집중 필요도 |
| postponeCount | integer | X | 기존 미루기 횟수 |
| completedMinutes | integer | X | 이미 완료한 시간 |
| remainingMinutes | integer | X | 직접 지정한 남은 시간 |
| completed | boolean | X | 작업 완료 여부 |
| prerequisiteTaskIds | array | X | 선행 작업 ID 목록 |

## fixedSchedules 필드

```json
{
  "fixedScheduleId": 10,
  "title": "전공 수업",
  "startTime": "2026-08-05T10:00:00",
  "endTime": "2026-08-05T12:00:00"
}
```

| 필드 | 자료형 | 필수 | 설명 |
|---|---:|---:|---|
| fixedScheduleId | integer | O | 고정 일정 ID |
| title | string | O | 일정 이름 |
| startTime | datetime | O | 시작 시각 |
| endTime | datetime | O | 종료 시각 |

## 요청 예시

```json
{
  "requestId": "generate-request-001",
  "userId": 1,
  "weekStartDate": "2026-08-03",
  "weekEndDate": "2026-08-09",
  "timezone": "Asia/Seoul",
  "tasks": [
    {
      "taskId": 1,
      "title": "발표 자료 조사",
      "estimatedMinutes": 60,
      "deadline": "2026-08-05T18:00:00",
      "priority": 2,
      "difficulty": 2,
      "focusRequired": 3
    }
  ],
  "fixedSchedules": [
    {
      "fixedScheduleId": 10,
      "title": "전공 수업",
      "startTime": "2026-08-05T10:00:00",
      "endTime": "2026-08-05T12:00:00"
    }
  ],
  "existingSchedules": []
}
```

## 정상 응답 예시

```json
{
  "success": true,
  "message": "일정 1개를 생성했습니다.",
  "requestId": "generate-request-001",
  "userId": 1,
  "weekStartDate": "2026-08-03",
  "weekEndDate": "2026-08-09",
  "timezone": "Asia/Seoul",
  "duplicateRequest": false,
  "schedules": [
    {
      "blockId": "generated:1:step-1",
      "taskId": "1",
      "title": "발표 자료 조사",
      "stepOrder": 1,
      "startTime": "2026-08-05T12:00:00",
      "endTime": "2026-08-05T13:00:00",
      "source": "GENERATED",
      "locked": false,
      "completed": false,
      "reasonCode": "NEAR_DEADLINE",
      "reason": "마감 임박도와 중요도를 반영하여 배치했습니다."
    }
  ],
  "preservedSchedules": [
    {
      "blockId": "fixed:10",
      "taskId": null,
      "title": "전공 수업",
      "stepOrder": null,
      "startTime": "2026-08-05T10:00:00",
      "endTime": "2026-08-05T12:00:00",
      "source": "FIXED",
      "locked": true,
      "completed": false,
      "reasonCode": null,
      "reason": null
    }
  ],
  "unscheduledTasks": [],
  "changes": [],
  "warnings": [],
  "scores": {},
  "summary": {
    "createdCount": 1,
    "preservedCount": 1,
    "unscheduledCount": 0,
    "warningCount": 0
  }
}
```

---

# 5. 일정 재배치

## POST `/ai/schedules/replan`

완료, 미루기, 긴급 일정, 고정 상태를 반영해 남은 일정을 재배치한다.

## 요청 형식

```json
{
  "requestId": "replan-request-001",
  "userId": 1,
  "weekStartDate": "2026-08-03",
  "weekEndDate": "2026-08-09",
  "timezone": "Asia/Seoul",
  "replanFromTime": "2026-08-05T08:00:00",
  "completedTaskIds": [],
  "postponedTaskIds": [],
  "tasks": [],
  "fixedSchedules": [],
  "existingSchedules": []
}
```

## 재배치 전용 필드

| 필드 | 자료형 | 필수 | 설명 |
|---|---:|---:|---|
| completedTaskIds | array | X | 완료 처리할 작업 ID 목록 |
| postponedTaskIds | array | X | 미루기 횟수를 증가시킬 작업 ID |
| replanFromTime | datetime | X | 이 시각 이후 일정을 재배치 |

## existingSchedules 필드

```json
{
  "blockId": "generated:1:step-1",
  "taskId": 1,
  "title": "발표 자료 조사",
  "stepOrder": 1,
  "startTime": "2026-08-05T09:00:00",
  "endTime": "2026-08-05T10:00:00",
  "source": "GENERATED",
  "locked": false,
  "completed": false,
  "reasonCode": "NEAR_DEADLINE",
  "reason": "마감 임박도를 반영하여 배치했습니다."
}
```

`locked=true`인 일정은 재배치 후에도 기존 시간을 유지한다.

`completed=true`이거나 `completedTaskIds`에 포함된 작업은 재배치 결과에서 제외한다.

## 재배치 요청 예시

```json
{
  "requestId": "replan-request-001",
  "userId": 1,
  "weekStartDate": "2026-08-03",
  "weekEndDate": "2026-08-09",
  "timezone": "Asia/Seoul",
  "replanFromTime": "2026-08-05T08:00:00",
  "completedTaskIds": [],
  "postponedTaskIds": [
    1
  ],
  "tasks": [
    {
      "taskId": 1,
      "title": "발표 자료 조사",
      "estimatedMinutes": 60,
      "deadline": "2026-08-05T18:00:00",
      "priority": 2,
      "difficulty": 2,
      "focusRequired": 3,
      "postponeCount": 0
    }
  ],
  "fixedSchedules": [
    {
      "fixedScheduleId": 20,
      "title": "긴급 병원 예약",
      "startTime": "2026-08-05T09:00:00",
      "endTime": "2026-08-05T11:00:00"
    }
  ],
  "existingSchedules": [
    {
      "blockId": "generated:1:step-1",
      "taskId": 1,
      "title": "발표 자료 조사",
      "stepOrder": 1,
      "startTime": "2026-08-05T09:00:00",
      "endTime": "2026-08-05T10:00:00",
      "source": "GENERATED",
      "locked": false,
      "completed": false
    }
  ]
}
```

## 재배치 응답 예시

```json
{
  "success": true,
  "message": "일정 재배치가 완료되었습니다.",
  "requestId": "replan-request-001",
  "userId": 1,
  "weekStartDate": "2026-08-03",
  "weekEndDate": "2026-08-09",
  "timezone": "Asia/Seoul",
  "duplicateRequest": false,
  "schedules": [
    {
      "blockId": "generated:1:step-1",
      "taskId": "1",
      "title": "발표 자료 조사",
      "stepOrder": 1,
      "startTime": "2026-08-05T11:30:00",
      "endTime": "2026-08-05T12:30:00",
      "source": "GENERATED",
      "locked": false,
      "completed": false,
      "reasonCode": "NEAR_DEADLINE",
      "reason": "마감 임박도와 미루기 횟수를 반영하여 배치했습니다."
    }
  ],
  "preservedSchedules": [
    {
      "blockId": "fixed:20",
      "taskId": null,
      "title": "긴급 병원 예약",
      "stepOrder": null,
      "startTime": "2026-08-05T09:00:00",
      "endTime": "2026-08-05T11:00:00",
      "source": "FIXED",
      "locked": true,
      "completed": false,
      "reasonCode": null,
      "reason": null
    }
  ],
  "finalSchedules": [],
  "unscheduledTasks": [],
  "changes": [
    {
      "sequence": 1,
      "action": "MOVED",
      "taskId": "1",
      "blockId": "generated:1:step-1",
      "title": "발표 자료 조사",
      "beforeStartTime": "2026-08-05T09:00:00",
      "beforeEndTime": "2026-08-05T10:00:00",
      "afterStartTime": "2026-08-05T11:30:00",
      "afterEndTime": "2026-08-05T12:30:00",
      "reasonCode": "NEAR_DEADLINE",
      "reason": "변경된 조건을 반영하여 이동했습니다."
    }
  ],
  "warnings": [],
  "scores": {},
  "summary": {
    "createdCount": 0,
    "splitCount": 0,
    "movedCount": 1,
    "keptCount": 0,
    "removedCount": 0,
    "preservedCount": 1,
    "unscheduledCount": 0,
    "warningCount": 0
  }
}
```

`finalSchedules`는 다음 두 배열을 합친 최종 결과이다.

```text
preservedSchedules + schedules
```

---

# 6. changes 액션

| action | 설명 |
|---|---|
| CREATED | 기존에 없던 일정이 새로 생성됨 |
| SPLIT | 긴 작업의 두 번째 이후 블록이 생성됨 |
| MOVED | 기존 일정의 시작 또는 종료 시간이 변경됨 |
| KEPT | 기존 일정이 같은 시간에 유지됨 |
| REMOVED | 기존 일정이 최종 결과에서 제거됨 |

## sequence 규칙

`changes`는 변경이 발생한 시간을 기준으로 오름차순 정렬한다.

정렬 시간은 다음 기준으로 결정한다.

```text
afterStartTime이 있으면 afterStartTime 사용
afterStartTime이 없으면 beforeStartTime 사용
```

정렬 후 `sequence`를 1부터 연속으로 부여한다.

```json
[
  {
    "sequence": 1
  },
  {
    "sequence": 2
  },
  {
    "sequence": 3
  }
]
```

---

# 7. 주요 reasonCode

| reasonCode | 설명 |
|---|---|
| NEAR_DEADLINE | 마감이 가까운 작업 |
| PREREQUISITE_ORDER | 선행 작업 순서 반영 |
| TASK_SPLIT | 긴 작업을 여러 블록으로 분할 |
| USER_LOCKED | 사용자가 고정한 일정 유지 |
| UNCHANGED | 기존 일정이 적절하여 유지 |
| REPLANNED | 변경 조건에 따라 이동 |
| TASK_COMPLETED | 완료 작업 제거 |
| REMOVED_FROM_PLAN | 변경 조건으로 기존 일정 제거 |
| INSUFFICIENT_TIME | 마감 전 배치 시간 부족 |
| DEADLINE_PASSED | 마감 시간이 이미 지남 |
| DEPENDENCY_CONFLICT | 선행 작업 충돌 |
| DEPENDENCY_CYCLE | 선행 작업 순환 참조 |
| NO_AVAILABLE_SLOT | 배치 가능한 빈 시간 없음 |
| INVALID_ESTIMATED_TIME | 예상 시간이 올바르지 않음 |

---

# 8. 배치 실패 응답

작업 일부를 배치하지 못하더라도 스케줄링 알고리즘 자체가 정상 실행됐다면:

```json
{
  "success": true
}
```

로 반환한다.

배치하지 못한 작업은 `unscheduledTasks`에 포함한다.

```json
{
  "taskId": "3",
  "title": "발표 연습",
  "requiredMinutes": 1200,
  "availableMinutes": 840,
  "reasonCode": "INSUFFICIENT_TIME",
  "reason": "마감일까지 360분의 추가 시간이 필요합니다."
}
```

---

# 9. 동일 requestId 중복 처리

동일한 엔드포인트에 같은 `requestId`가 다시 전달되면 기존 결과를 반환한다.

첫 번째 요청:

```json
{
  "requestId": "duplicate-request-001",
  "duplicateRequest": false
}
```

두 번째 요청:

```json
{
  "requestId": "duplicate-request-001",
  "duplicateRequest": true
}
```

generate와 replan은 서로 다른 캐시 키를 사용한다.

```text
generate:duplicate-request-001
replan:duplicate-request-001
```

따라서 두 엔드포인트에서 같은 `requestId`를 사용해도 서로 충돌하지 않는다.

현재 중복 요청 기록은 AI 서버 메모리에 저장된다.

서버를 재시작하면 캐시가 초기화된다.

운영 환경에서는 Spring Boot의 데이터베이스 또는 Redis에서 중복 요청을 관리하는 방식을 권장한다.

---

# 10. HTTP 상태 코드

| 상태 코드 | 설명 |
|---:|---|
| 200 | 요청 처리 또는 스케줄링 정상 완료 |
| 400 | 요청 필드 또는 값이 올바르지 않음 |
| 422 | FastAPI 요청 모델 검증 실패 |
| 500 | AI 일정 생성 또는 재배치 중 내부 오류 |

## 400 오류 응답 예시

```json
{
  "success": false,
  "requestId": "request-001",
  "duplicateRequest": false,
  "message": "일정 생성 요청값이 올바르지 않습니다.",
  "errorCode": "INVALID_REQUEST",
  "detail": "오류 상세 내용",
  "schedules": [],
  "preservedSchedules": [],
  "unscheduledTasks": [],
  "changes": []
}
```

## 500 오류 응답 예시

```json
{
  "success": false,
  "requestId": "request-001",
  "duplicateRequest": false,
  "message": "AI 일정 생성 중 오류가 발생했습니다.",
  "errorCode": "SCHEDULING_ERROR",
  "detail": "오류 상세 내용",
  "schedules": [],
  "preservedSchedules": [],
  "unscheduledTasks": [],
  "changes": []
}
```

---

# 11. 백엔드 연동 전 최종 확인 사항

백엔드 담당자와 아래 항목을 최종 합의해야 한다.

```text
1. taskId를 integer 또는 string 중 어떤 형식으로 통일할지
2. generate 최종 API 경로
3. requestId를 어느 서버에서 영구 관리할지
4. duplicateRequest 필드 사용 여부
5. warnings 내부의 snake_case 필드를 변환할지
6. reasonCode 목록을 enum으로 관리할지
7. AI 서버 주소와 포트
8. HTTP 연결 시간 제한과 재시도 정책
```