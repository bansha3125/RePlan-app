package com.replan.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.replan.api.dto.*;
import com.replan.api.entity.FixedSchedule;
import com.replan.api.entity.GeneratedSchedule;
import com.replan.api.entity.Task;
import com.replan.api.repository.FixedScheduleRepository;
import com.replan.api.repository.GeneratedScheduleRepository;
import com.replan.api.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final String AI_SERVER_URL =
            "http://127.0.0.1:8000/schedules/generate";

    private static final String AI_REPLAN_URL =
            "http://127.0.0.1:8000/ai/schedules/replan";

    private static final String TIMEZONE = "Asia/Seoul";

    /*
     * 현재 Task 입력 DTO/Entity에 AI 명세의 priority 필드가 없으므로
     * 임시 기본값을 사용한다. 추후 프론트 입력값과 DB 컬럼을 연결하면
     * task.getPriority() 값으로 교체한다.
     */
    private static final int DEFAULT_PRIORITY = 2;

    private final FixedScheduleRepository fixedRepository;
    private final GeneratedScheduleRepository generatedRepository;
    private final TaskRepository taskRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final jakarta.persistence.EntityManager entityManager;

    // ==========================================
    // 1. 조회 및 기본 CRUD 영역
    // ==========================================

    public WeeklyScheduleResponse getWeeklySchedules(Long userId, String weekStartDateParam) {
        LocalDate weekStartDate = (weekStartDateParam != null && !weekStartDateParam.isBlank())
                ? LocalDate.parse(weekStartDateParam)
                : getCurrentWeekStart();

        LocalDate weekEndDate = weekStartDate.plusDays(6);

        LocalDateTime startDateTime = weekStartDate.atStartOfDay();
        LocalDateTime endDateTime = weekEndDate.atTime(23, 59, 59);

        // 고정 일정 조회 및 변환
        List<FixedScheduleDto> fixedDtos = fixedRepository.findByUserId(userId)
                .stream()
                .sorted(Comparator.comparing(FixedSchedule::getStartTime))
                .map(f -> FixedScheduleDto.builder()
                        .fixedScheduleId(f.getFixedScheduleId())
                        .title(f.getTitle())
                        .startTime(f.getStartTime().toString())
                        .endTime(f.getEndTime().toString())
                        .repeatDay(f.getRepeatDay())
                        .locked(true)
                        .build())
                .toList();

        // 생성된 일정 조회 및 변환
        List<GeneratedScheduleDto> generatedDtos =
                generatedRepository.findByUserIdAndStartTimeBetween(userId, startDateTime, endDateTime)
                        .stream()
                        .sorted(Comparator.comparing(GeneratedSchedule::getStartTime)
                                .thenComparing(GeneratedSchedule::getStepOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(g -> GeneratedScheduleDto.builder()
                                .blockId(g.getBlockId())
                                .taskId(g.getTaskId())
                                .title(g.getTitle())
                                .stepOrder(g.getStepOrder())
                                .startTime(g.getStartTime().toString())
                                .endTime(g.getEndTime().toString())
                                .source(g.getSource())
                                .locked(Boolean.TRUE.equals(g.getLocked()))
                                .completed(Boolean.TRUE.equals(g.getCompleted()))
                                .reasonCode(g.getReasonCode())
                                .reason(g.getReason())
                                .build())
                        .toList();

        return WeeklyScheduleResponse.builder()
                .fixedSchedules(fixedDtos)
                .generatedSchedules(generatedDtos)
                .build();
    }

    public List<TaskResponse> getTasks(Long userId, String weekStartDateParam) {
        LocalDate weekStartDate = (weekStartDateParam != null && !weekStartDateParam.isBlank())
                ? LocalDate.parse(weekStartDateParam)
                : getCurrentWeekStart();

        LocalDate weekEndDate = weekStartDate.plusDays(6);
        LocalDateTime startDateTime = weekStartDate.atStartOfDay();
        LocalDateTime endDateTime = weekEndDate.atTime(23, 59, 59);

        return taskRepository.findByUserId(userId)
                .stream()
                .filter(task -> {
                    if (task.getDeadline() == null) {
                        return false;
                    }
                    return !task.getDeadline().isBefore(startDateTime) && !task.getDeadline().isAfter(endDateTime);
                })
                .sorted(Comparator.comparing(Task::isCompleted)
                        .thenComparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(task -> TaskResponse.builder()
                        .taskId(task.getTaskId())
                        .title(task.getTitle())
                        .deadline(task.getDeadline() != null ? task.getDeadline().toString() : null)
                        .estimatedMinutes(task.getEstimatedMinutes())
                        .completed(task.isCompleted())
                        .priority(task.getPriority())
                        .desiredSteps(task.getDesiredSteps())
                        .build())
                .toList();
    }

    public void saveFixedSchedule(FixedScheduleRequest request) {
        fixedRepository.save(FixedSchedule.builder()
                .userId(request.getUserId())
                .title(request.getTitle())
                .startTime(LocalDateTime.parse(request.getStartTime()))
                .endTime(LocalDateTime.parse(request.getEndTime()))
                .repeatDay(request.getRepeatDay())
                .build());
    }

    public void saveTask(TaskRequest request) {
        taskRepository.save(Task.builder()
                .userId(request.getUserId())
                .title(request.getTitle())
                .deadline(LocalDateTime.parse(request.getDeadline()))
                .estimatedMinutes(request.getEstimatedMinutes())
                .useAiDecomposition(Boolean.TRUE.equals(request.getUseAiDecomposition()))
                .desiredSteps(request.getDesiredSteps())
                .priority(request.getPriority() != null ? request.getPriority() : 2)
                .build());
    }

    @Transactional
    public void updateTask(Long taskId, TaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 할 일입니다: " + taskId));

        task.update(
                request.getTitle(),
                LocalDateTime.parse(request.getDeadline()),
                request.getEstimatedMinutes(),
                Boolean.TRUE.equals(request.getUseAiDecomposition()),
                request.getDesiredSteps(),
                request.getPriority() != null ? request.getPriority() : 2
        );

        taskRepository.save(task);
    }

    @Transactional
    public void updateTaskCompletion(Long taskId, boolean completed) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("해당 Task가 없습니다. id=" + taskId));
        task.updateCompleted(completed);
    }

    @Transactional
    public void updateGeneratedSchedule(String blockId, Boolean locked, Boolean completed, String startTime, String endTime) {
        GeneratedSchedule schedule = generatedRepository.findByBlockId(blockId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일정을 찾을 수 없습니다: " + blockId));

        if (locked != null) schedule.updateLocked(locked);

        if (completed != null) {
            schedule.updateCompleted(completed);

            if (completed && schedule.getTaskId() != null) {
                checkAndCompleteTask(schedule.getTaskId());
            } else if (!completed && schedule.getTaskId() != null) {
                Task task = taskRepository.findById(schedule.getTaskId()).orElse(null);
                if (task != null && task.isCompleted()) {
                    task.updateCompleted(false);
                    taskRepository.save(task);
                }
            }
        }

        if (startTime != null && !startTime.isBlank()) schedule.updateStartTime(LocalDateTime.parse(startTime));
        if (endTime != null && !endTime.isBlank()) schedule.updateEndTime(LocalDateTime.parse(endTime));

        generatedRepository.saveAndFlush(schedule);
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        generatedRepository.deleteByTaskId(taskId);
        taskRepository.deleteById(taskId);
    }

    // ==========================================
    // 2. AI 스케줄 생성 및 재배치(Replan) 영역
    // ==========================================

    @Transactional
    public ReplanResultResponse generateAiSchedule(Long userId, String weekStartDateParam) {
        LocalDate weekStartDate = (weekStartDateParam != null && !weekStartDateParam.isBlank())
                ? LocalDate.parse(weekStartDateParam)
                : getCurrentWeekStart();

        LocalDate weekEndDate = weekStartDate.plusDays(6);

        List<AiTaskRequest> aiTasks = buildAiTasks(userId);
        validateUniqueTaskIds(aiTasks);

        // AI 요청 페이로드 구성
        GenerateScheduleRequest aiRequest =
                GenerateScheduleRequest.builder()
                        .requestId("generate-user-" + userId + "-" + UUID.randomUUID())
                        .userId(userId)
                        .weekStartDate(weekStartDate.toString())
                        .weekEndDate(weekEndDate.toString())
                        .timezone(TIMEZONE)
                        .tasks(aiTasks)
                        .fixedSchedules(buildAiFixedSchedules(userId))
                        .existingSchedules(new ArrayList<>())
                        .build();

        // AI 서버 통신
        AiScheduleResponse response = postToAi(AI_SERVER_URL, aiRequest);

        List<AiScheduleBlockResponse> generatedBlocks =
                extractGeneratedBlocks(response);

        Map<String, List<AiScheduleBlockResponse>> groupedByTask = generatedBlocks.stream()
                .filter(block -> block.getTaskId() != null && !block.getTaskId().isBlank())
                .collect(Collectors.groupingBy(AiScheduleBlockResponse::getTaskId));

        List<AiScheduleBlockResponse> safelyControlledBlocks = new ArrayList<>();

        for (Map.Entry<String, List<AiScheduleBlockResponse>> entry : groupedByTask.entrySet()) {
            List<AiScheduleBlockResponse> blocks = entry.getValue();
            blocks.sort(Comparator.comparing(b -> b.getStepOrder() != null ? b.getStepOrder() : 0));
            safelyControlledBlocks.addAll(blocks);
        }

        for (AiScheduleBlockResponse block : generatedBlocks) {
            if (block.getTaskId() == null || block.getTaskId().isBlank()) {
                safelyControlledBlocks.add(block);
            }
        }

        log.info(
                "AI 스케줄 생성 성공: 생성 일정 {}개, 미배치 작업 {}개",
                safelyControlledBlocks.size(),
                sizeOf(response.getUnscheduledTasks())
        );

        saveGeneratedSchedules(safelyControlledBlocks, userId);
        logAiWarnings(response);

        // 경고 및 미배치 사유 수집 및 정제
        List<String> rawWarnings = new ArrayList<>();
        if (response.getWarnings() != null) {
            rawWarnings.addAll(response.getWarnings().stream()
                    .map(AiWarningResponse::getMessage)
                    .toList());
        }
        if (response.getUnscheduledTasks() != null) {
            rawWarnings.addAll(response.getUnscheduledTasks().stream()
                    .map(AiUnscheduledTaskResponse::getReason)
                    .toList());
        }

        List<String> distinctWarnings = rawWarnings.stream()
                .distinct()
                .toList();

        List<String> warningMessages = new ArrayList<>();
        boolean hasDeadlineWarning = distinctWarnings.stream().anyMatch(w -> w.contains("마감") || w.contains("시작 시간"));

        if (hasDeadlineWarning) {
            distinctWarnings.stream()
                    .filter(w -> w.contains("마감") || w.contains("시작 시간"))
                    .findFirst()
                    .ifPresent(warningMessages::add);
        } else {
            warningMessages.addAll(distinctWarnings);
        }

        if (!warningMessages.isEmpty()) {
            return ReplanResultResponse.builder()
                    .success(false)
                    .message("일부 일정을 생성하지 못했습니다.")
                    .warnings(warningMessages)
                    .build();
        }

        return ReplanResultResponse.builder()
                .success(true)
                .message("AI 스케줄 생성 및 DB 저장 요청 완료")
                .warnings(List.of())
                .build();
    }

    @Transactional
    public ReplanResultResponse replanAiSchedule(
            Long userId,
            String replanFromTime,
            List<Long> completedTaskIds,
            List<Long> postponedTaskIds,
            List<String> postponedBlockIds
    ) {
        LocalDate weekStartDate = getCurrentWeekStart();
        LocalDate weekEndDate = weekStartDate.plusDays(6);

        List<AiTaskRequest> aiTasks = buildAiTasks(userId);
        validateUniqueTaskIds(aiTasks);

        // AI 재배치 요청 페이로드 구성
        ReplanRequest aiRequest = ReplanRequest.builder()
                .requestId("replan-user-" + userId + "-" + UUID.randomUUID())
                .userId(userId)
                .weekStartDate(weekStartDate.toString())
                .weekEndDate(weekEndDate.toString())
                .timezone(TIMEZONE)
                .replanFromTime(replanFromTime)
                .completedTaskIds(toStringIds(completedTaskIds))
                .postponedTaskIds(toStringIds(postponedTaskIds))
                .postponedBlockIds(postponedBlockIds)
                .tasks(aiTasks)
                .fixedSchedules(buildAiFixedSchedules(userId))
                .existingSchedules(buildExistingSchedules(userId))
                .build();

        // AI 서버 통신
        AiScheduleResponse response = postToAi(AI_REPLAN_URL, aiRequest);

        if (response.getChanges() != null && !response.getChanges().isEmpty()) {
            for (AiScheduleChangeResponse change : response.getChanges()) {
                if (change.getBlockId() != null && change.getAfterStartTime() != null) {

                    if (postponedBlockIds != null && !postponedBlockIds.isEmpty()) {
                        if (!postponedBlockIds.contains(change.getBlockId())) {
                            log.info("[REPLAN SCOPE GUARD] 요청된 미루기 대상이 아니므로 제외합니다: blockId={}", change.getBlockId());
                            continue;
                        }
                    }

                    GeneratedSchedule schedule = generatedRepository.findByBlockId(change.getBlockId()).orElse(null);

                    if (schedule != null) {
                        if (Boolean.TRUE.equals(schedule.getCompleted()) || Boolean.TRUE.equals(schedule.getLocked())) {
                            log.info("[REPLAN SKIP] 완료 또는 잠금 상태인 블록은 제외됩니다: blockId={}", schedule.getBlockId());
                            continue;
                        }

                        if (change.getAfterStartTime() != null) {
                            schedule.updateStartTime(LocalDateTime.parse(change.getAfterStartTime()));
                        }
                        if (change.getAfterEndTime() != null) {
                            schedule.updateEndTime(LocalDateTime.parse(change.getAfterEndTime()));
                        }

                        generatedRepository.save(schedule);
                        log.info("[REPLAN UPDATE] blockId={}, newStart={}, newEnd={}",
                                schedule.getBlockId(), schedule.getStartTime(), schedule.getEndTime());
                    }
                }
            }
        } else {
            log.warn("[REPLAN] AI 응답에 changes가 없어 시간 업데이트가 스킵되었습니다.");
        }

        log.info(
                "AI 일정 재배치 완료: 변경사항 {}개 반영됨",
                sizeOf(response.getChanges())
        );

        logAiWarnings(response);

        List<String> warningMessages = new ArrayList<>();
        if (response.getWarnings() != null) {
            warningMessages.addAll(response.getWarnings().stream()
                    .map(AiWarningResponse::getMessage)
                    .toList());
        }
        if (response.getUnscheduledTasks() != null) {
            warningMessages.addAll(response.getUnscheduledTasks().stream()
                    .map(AiUnscheduledTaskResponse::getReason)
                    .toList());
        }

        if (!warningMessages.isEmpty()) {
            return ReplanResultResponse.builder()
                    .success(false)
                    .message("일부 일정을 재배치할 수 없습니다.")
                    .warnings(warningMessages)
                    .build();
        }

        return ReplanResultResponse.builder()
                .success(true)
                .message("AI 일정 재배치 및 DB 반영 요청 완료!")
                .warnings(List.of())
                .build();
    }

    // ==========================================
    // 3. 내부 헬퍼 및 유틸리티 영역
    // ==========================================

    private void checkAndCompleteTask(Long taskId) {
        if (!generatedRepository.existsByTaskIdAndCompletedFalse(taskId)) {
            Task task = taskRepository.findById(taskId).orElseThrow();
            task.updateCompleted(true);
            taskRepository.save(task);
        }
    }

    private List<AiTaskRequest> buildAiTasks(Long userId) {
        return taskRepository.findByUserId(userId)
                .stream()
                .map(task -> {
                    int mappedPriority = convertPriorityToAiScale(task.getPriority());

                    int difficulty = (task.getDifficulty() != null && task.getDifficulty() > 0) ? task.getDifficulty() : 3;
                    int focusRequired = (task.getFocusRequired() != null && task.getFocusRequired() > 0) ? task.getFocusRequired() : 3;
                    int desiredSteps = (task.getDesiredSteps() > 0) ? task.getDesiredSteps() : 3;

                    return AiTaskRequest.builder()
                            .taskId(String.valueOf(task.getTaskId()))
                            .title(task.getTitle())
                            .estimatedMinutes(task.getEstimatedMinutes())
                            .deadline(task.getDeadline().toString())
                            .priority(mappedPriority)
                            .difficulty(difficulty)
                            .focusRequired(focusRequired)
                            .postponeCount(task.getPostponeCount())
                            .completedMinutes(task.getCompletedMinutes())
                            .remainingMinutes(task.getEstimatedMinutes())
                            .completed(task.isCompleted())
                            .prerequisiteTaskIds(new ArrayList<>())
                            .useAiDecomposition(task.isUseAiDecomposition())
                            .desiredSteps(desiredSteps)
                            .build();
                })
                .toList();
    }

    private int convertPriorityToAiScale(Integer frontPriority) {
        if (frontPriority == null) {
            return 3;
        }
        switch (frontPriority) {
            case 3:
                return 5;
            case 2:
                return 3;
            case 1:
                return 1;
            default:
                return frontPriority;
        }
    }

    private List<AiFixedScheduleRequest> buildAiFixedSchedules(Long userId) {
        return fixedRepository.findByUserId(userId)
                .stream()
                .map(fixed -> AiFixedScheduleRequest.builder()
                        .fixedScheduleId(fixed.getFixedScheduleId())
                        .title(fixed.getTitle())
                        .startTime(fixed.getStartTime().toString())
                        .endTime(fixed.getEndTime().toString())
                        .build())
                .toList();
    }

    private List<AiExistingScheduleRequest> buildExistingSchedules(Long userId) {
        return generatedRepository.findByUserId(userId)
                .stream()
                .map(schedule -> AiExistingScheduleRequest.builder()
                        .blockId(schedule.getBlockId())
                        .taskId(
                                schedule.getTaskId() == null
                                        ? null
                                        : String.valueOf(schedule.getTaskId())
                        )
                        .title(schedule.getTitle())
                        .stepOrder(schedule.getStepOrder())
                        .startTime(schedule.getStartTime().toString())
                        .endTime(schedule.getEndTime().toString())
                        .source(schedule.getSource())
                        .locked(Boolean.TRUE.equals(schedule.getLocked()))
                        .completed(Boolean.TRUE.equals(schedule.getCompleted()))
                        .reasonCode(schedule.getReasonCode())
                        .reason(schedule.getReason())
                        .build())
                .toList();
    }

    private AiScheduleResponse postToAi(String url, Object request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        log.info("[BACKEND -> AI] {}", toJson(request));

        try {
            ResponseEntity<AiScheduleResponse> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            new HttpEntity<>(request, headers),
                            AiScheduleResponse.class
                    );

            AiScheduleResponse body = response.getBody();

            log.info(
                    "[AI -> BACKEND] status={}, body={}",
                    response.getStatusCode(),
                    toJson(body)
            );

            if (body == null) {
                throw new IllegalStateException("AI 서버 응답 본문이 비어 있습니다.");
            }

            if (!Boolean.TRUE.equals(body.getSuccess())) {
                throw new IllegalStateException(
                        "AI 서버 요청 실패: " + body.getMessage()
                );
            }

            return body;

        } catch (Exception exception) {
            log.error(
                    "AI 서버 통신 실패: url={}, message={}",
                    url,
                    exception.getMessage(),
                    exception
            );
            throw new IllegalStateException(
                    "AI 서버 통신에 실패했습니다.",
                    exception
            );
        }
    }

    private List<AiScheduleBlockResponse> extractGeneratedBlocks(
            AiScheduleResponse response
    ) {
        List<AiScheduleBlockResponse> candidates = new ArrayList<>();

        if (response.getFinalSchedules() != null
                && !response.getFinalSchedules().isEmpty()) {
            candidates.addAll(response.getFinalSchedules());
        } else {
            if (response.getSchedules() != null) {
                candidates.addAll(response.getSchedules());
            }

            if (response.getPreservedSchedules() != null) {
                candidates.addAll(response.getPreservedSchedules());
            }
        }

        Map<String, AiScheduleBlockResponse> uniqueSchedules =
                new LinkedHashMap<>();

        for (AiScheduleBlockResponse block : candidates) {
            if (block == null || block.getTaskId() == null) {
                continue;
            }

            String key = block.getBlockId();

            if (key == null || key.isBlank()) {
                key = block.getTaskId()
                        + "|"
                        + block.getStartTime()
                        + "|"
                        + block.getEndTime();
            }

            uniqueSchedules.put(key, block);
        }

        return new ArrayList<>(uniqueSchedules.values());
    }

    private void saveGeneratedSchedules(
            List<AiScheduleBlockResponse> aiResponse,
            Long userId
    ) {
        Set<String> completedBlockIds = generatedRepository.findByUserId(userId)
                .stream()
                .filter(schedule -> Boolean.TRUE.equals(schedule.getCompleted()))
                .map(GeneratedSchedule::getBlockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<GeneratedSchedule> completedSchedulesBackup = generatedRepository.findByUserId(userId)
                .stream()
                .filter(schedule -> Boolean.TRUE.equals(schedule.getCompleted()))
                .toList();

        generatedRepository.deleteByUserId(userId);

        List<Task> userTasks = taskRepository.findByUserId(userId);
        Map<Long, Integer> taskDesiredStepsMap = userTasks.stream()
                .collect(Collectors.toMap(Task::getTaskId, Task::getDesiredSteps, (existing, replacement) -> existing));

        List<AiScheduleBlockResponse> controlledAiResponse = new ArrayList<>();
        if (aiResponse != null && !aiResponse.isEmpty()) {
            Map<String, List<AiScheduleBlockResponse>> groupedByTask = aiResponse.stream()
                    .filter(block -> block.getTaskId() != null && !block.getTaskId().isBlank())
                    .collect(Collectors.groupingBy(AiScheduleBlockResponse::getTaskId));

            for (Map.Entry<String, List<AiScheduleBlockResponse>> entry : groupedByTask.entrySet()) {
                String taskIdStr = entry.getKey();
                List<AiScheduleBlockResponse> blocks = entry.getValue();
                Long taskId = parseLongTaskId(taskIdStr);

                blocks.sort(Comparator.comparing(b -> b.getStepOrder() != null ? b.getStepOrder() : 0));

                Integer targetSteps = taskId != null ? taskDesiredStepsMap.get(taskId) : null;

                if (targetSteps != null && targetSteps > 0 && blocks.size() > targetSteps) {
                    log.warn("AI가 요청된 단계 수({})를 초과하여 {}개의 블록을 생성했습니다. taskId={} 블록을 limit({})하여 잘라냅니다.",
                            targetSteps, blocks.size(), taskId, targetSteps);
                    blocks = blocks.stream().limit(targetSteps).collect(Collectors.toList());
                }
                controlledAiResponse.addAll(blocks);
            }

            aiResponse.stream()
                    .filter(block -> block.getTaskId() == null || block.getTaskId().isBlank())
                    .forEach(controlledAiResponse::add);
        }

        List<GeneratedSchedule> newSchedules = new ArrayList<>();
        if (!controlledAiResponse.isEmpty()) {
            newSchedules = controlledAiResponse.stream()
                    .map(block -> {
                        Long parsedTaskId = parseLongTaskId(block.getTaskId());

                        String finalTitle = block.getTitle();
                        if (parsedTaskId != null) {
                            Task task = taskRepository.findById(parsedTaskId).orElse(null);
                            if (task != null && task.getTitle() != null) {
                                String prefix = "[" + task.getTitle() + "]";
                                if (finalTitle == null || !finalTitle.startsWith(prefix)) {
                                    finalTitle = prefix + " " + block.getTitle();
                                }
                            }
                        }

                        boolean isCompleted = Boolean.TRUE.equals(block.getCompleted())
                                || completedBlockIds.contains(block.getBlockId());

                        LocalDateTime finalStartTime = parseDateTime(block.getStartTime(), "startTime");
                        LocalDateTime finalEndTime = parseDateTime(block.getEndTime(), "endTime");

                        if (isCompleted) {
                            GeneratedSchedule oldSchedule = completedSchedulesBackup.stream()
                                    .filter(s ->
                                            (s.getBlockId() != null && s.getBlockId().equals(block.getBlockId())) ||
                                                    (s.getTaskId() != null && s.getTaskId().equals(parsedTaskId)
                                                            && s.getStepOrder() != null && s.getStepOrder().equals(block.getStepOrder()))
                                    )
                                    .findFirst()
                                    .orElse(null);

                            if (oldSchedule != null) {
                                finalStartTime = oldSchedule.getStartTime();
                                finalEndTime = oldSchedule.getEndTime();
                            }
                        }

                        return GeneratedSchedule.builder()
                                .userId(userId)
                                .taskId(parsedTaskId)
                                .title(finalTitle)
                                .startTime(finalStartTime)
                                .endTime(finalEndTime)
                                .blockId(block.getBlockId())
                                .stepOrder(block.getStepOrder())
                                .source(block.getSource())
                                .locked(Boolean.TRUE.equals(block.getLocked()))
                                .completed(isCompleted)
                                .reasonCode(block.getReasonCode())
                                .reason(block.getReason())
                                .build();
                    })
                    .toList();
        }

        List<GeneratedSchedule> finalSchedulesToSave = new ArrayList<>(newSchedules);

        Set<String> newBlockIds = newSchedules.stream()
                .map(GeneratedSchedule::getBlockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (GeneratedSchedule oldSchedule : completedSchedulesBackup) {
            if (!newBlockIds.contains(oldSchedule.getBlockId())) {
                finalSchedulesToSave.add(
                        GeneratedSchedule.builder()
                                .userId(oldSchedule.getUserId())
                                .taskId(oldSchedule.getTaskId())
                                .title(oldSchedule.getTitle())
                                .startTime(oldSchedule.getStartTime())
                                .endTime(oldSchedule.getEndTime())
                                .blockId(oldSchedule.getBlockId())
                                .stepOrder(oldSchedule.getStepOrder())
                                .source(oldSchedule.getSource())
                                .locked(oldSchedule.getLocked())
                                .completed(true)
                                .reasonCode(oldSchedule.getReasonCode())
                                .reason(oldSchedule.getReason())
                                .build()
                );
            }
        }

        generatedRepository.saveAll(finalSchedulesToSave);
    }

    private Long parseLongTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(taskId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "AI가 반환한 taskId가 Long 형식이 아닙니다: " + taskId,
                    exception
            );
        }
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "AI 응답의 " + fieldName + " 값이 비어 있습니다."
            );
        }

        return LocalDateTime.parse(value);
    }

    private void validateUniqueTaskIds(List<AiTaskRequest> tasks) {
        Set<String> uniqueIds = new HashSet<>();

        for (AiTaskRequest task : tasks) {
            if (task.getTaskId() == null || task.getTaskId().isBlank()) {
                throw new IllegalStateException(
                        "AI 요청 작업에 taskId가 없는 항목이 있습니다."
                );
            }

            if (!uniqueIds.add(task.getTaskId())) {
                throw new IllegalStateException(
                        "AI 요청에 중복된 taskId가 있습니다: "
                                + task.getTaskId()
                );
            }
        }
    }

    private List<String> toStringIds(List<Long> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }

        return ids.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private LocalDate getCurrentWeekStart() {
        return LocalDate.now(ZoneId.of(TIMEZONE))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void logAiWarnings(AiScheduleResponse response) {
        if (response.getWarnings() != null
                && !response.getWarnings().isEmpty()) {
            log.warn("[AI WARNINGS] {}", toJson(response.getWarnings()));
        }
    }

    private int sizeOf(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }
}