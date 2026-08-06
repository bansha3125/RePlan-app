# RePlan AI DTO 수정본 적용 순서

## 핵심 원칙

- `TaskRequest`와 `FixedScheduleRequest`는 프론트 → 백엔드 CRUD 요청용이므로 유지한다.
- AI 서버와 통신할 때는 `Ai*` DTO를 별도로 사용한다.
- 백엔드 DB/프론트의 `taskId`는 `Long`으로 유지한다.
- 백엔드 ↔ AI 경계의 `taskId`는 `String`으로 직렬화한다.
- 날짜와 시간은 `YYYY-MM-DDTHH:MM:SS` 문자열로 보낸다.
- 빈 배열은 `null` 대신 `[]`로 보낸다.

## 교체할 파일

- `ReplanRequest.java`
- `GeneratedScheduleDto.java`
- `WeeklyScheduleResponse.java`

나머지 기존 파일은 목적을 명확히 하는 주석만 추가했다.

## 새로 추가할 파일

- `AiTaskRequest.java`
- `AiFixedScheduleRequest.java`
- `AiExistingScheduleRequest.java`
- `GenerateScheduleRequest.java`
- `AiScheduleBlockResponse.java`
- `AiUnscheduledTaskResponse.java`
- `AiScheduleChangeResponse.java`
- `AiWarningResponse.java`
- `AiScheduleSummaryResponse.java`
- `AiScheduleResponse.java`
- `AiScheduleMapper.java`

## AI 작업 요청 생성 예시

```java
AiTaskRequest aiTask = AiTaskRequest.builder()
        .taskId(String.valueOf(task.getId()))
        .title(task.getTitle())
        .estimatedMinutes(task.getEstimatedMinutes())
        .deadline(task.getDeadline().toString())
        .priority(task.getPriority())
        .difficulty(task.getDifficulty())
        .focusRequired(task.getFocusRequired())
        .postponeCount(task.getPostponeCount() == null ? 0 : task.getPostponeCount())
        .completedMinutes(task.getCompletedMinutes() == null ? 0 : task.getCompletedMinutes())
        .remainingMinutes(task.getRemainingMinutes())
        .completed(Boolean.TRUE.equals(task.getCompleted()))
        .prerequisiteTaskIds(
                task.getPrerequisiteTaskIds() == null
                        ? new java.util.ArrayList<>()
                        : task.getPrerequisiteTaskIds()
                                .stream()
                                .map(String::valueOf)
                                .toList()
        )
        .build();
```

## generate 요청 예시

```java
GenerateScheduleRequest request = GenerateScheduleRequest.builder()
        .requestId(java.util.UUID.randomUUID().toString())
        .userId(userId)
        .weekStartDate(weekStartDate.toString())
        .weekEndDate(weekEndDate.toString())
        .timezone("Asia/Seoul")
        .tasks(aiTasks)
        .fixedSchedules(aiFixedSchedules)
        .existingSchedules(new java.util.ArrayList<>())
        .build();
```

## replan 요청 예시

```java
ReplanRequest request = ReplanRequest.builder()
        .requestId(java.util.UUID.randomUUID().toString())
        .userId(userId)
        .weekStartDate(weekStartDate.toString())
        .weekEndDate(weekEndDate.toString())
        .timezone("Asia/Seoul")
        .replanFromTime(replanFromTime.toString())
        .completedTaskIds(
                completedTaskIds.stream()
                        .map(String::valueOf)
                        .toList()
        )
        .postponedTaskIds(
                postponedTaskIds.stream()
                        .map(String::valueOf)
                        .toList()
        )
        .tasks(aiTasks)
        .fixedSchedules(aiFixedSchedules)
        .existingSchedules(aiExistingSchedules)
        .build();
```

## AI 응답 변환 예시

```java
List<GeneratedScheduleDto> generatedSchedules =
        aiResponse.getSchedules()
                .stream()
                .map(AiScheduleMapper::toGeneratedScheduleDto)
                .toList();
```

## 반드시 확인할 로그

```java
log.info(
        "[BACKEND -> AI] {}",
        objectMapper.writeValueAsString(request)
);

log.info(
        "[AI -> BACKEND] {}",
        objectMapper.writeValueAsString(aiResponse)
);
```

전송 직전 `tasks` 안에 서로 다른 `taskId`와 `title`이 들어가는지 확인한다.
