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

    public WeeklyScheduleResponse getWeeklySchedules(Long userId, String weekStartDateParam) {
        LocalDate weekStartDate = (weekStartDateParam != null && !weekStartDateParam.isBlank())
                ? LocalDate.parse(weekStartDateParam)
                : getCurrentWeekStart();

        LocalDate weekEndDate = weekStartDate.plusDays(6);

        // 날짜 범위를 LocalDateTime으로 변환 (시작일 00:00:00 ~ 종료일 23:59:59)
        LocalDateTime startDateTime = weekStartDate.atStartOfDay();
        LocalDateTime endDateTime = weekEndDate.atTime(23, 59, 59);

        // 1. 고정 일정 정렬 (1순위: 시작시간, 2순위: 제목 오름차순 등 필요시 설정)
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

        // 2. 생성된 일정 정렬 (1순위: 시작시간 오름차순, 2순위: stepOrder 순서 등)
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

        // 1. 일정 완료 상태 업데이트
        if (completed != null) {
            schedule.updateCompleted(completed);

            // 2. 일정이 완료(true)되었다면 같은 taskId를 가진 다른 일정이 남았는지 확인 후 Task 완료 처리
            if (completed && schedule.getTaskId() != null) {
                checkAndCompleteTask(schedule.getTaskId());
            }
            // 3. 반대로 일정이 미완료(false)로 바뀌었다면, 부모 Task도 무조건 미완료(false)로 되돌린다.
            else if (!completed && schedule.getTaskId() != null) {
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

    private void checkAndCompleteTask(Long taskId) {
        if (!generatedRepository.existsByTaskIdAndCompletedFalse(taskId)) {
            Task task = taskRepository.findById(taskId).orElseThrow();
            task.updateCompleted(true);
            taskRepository.save(task);
        }
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        // 1. 관련 AI 생성 일정 먼저 삭제
        generatedRepository.deleteByTaskId(taskId);

        // 2. 본체 Task 삭제
        taskRepository.deleteById(taskId);
    }

    @Transactional
    public void generateAiSchedule(Long userId, String weekStartDateParam) {

        LocalDate weekStartDate = (weekStartDateParam != null && !weekStartDateParam.isBlank())
                ? LocalDate.parse(weekStartDateParam)
                : getCurrentWeekStart();

        LocalDate weekEndDate = weekStartDate.plusDays(6);

        List<AiTaskRequest> aiTasks = buildAiTasks(userId);
        validateUniqueTaskIds(aiTasks);

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
    }

    @Transactional
    public void replanAiSchedule(
            Long userId,
            String replanFromTime,
            List<Long> completedTaskIds,
            List<Long> postponedTaskIds
    ) {
        LocalDate weekStartDate = getCurrentWeekStart();
        LocalDate weekEndDate = weekStartDate.plusDays(6);

        List<AiTaskRequest> aiTasks = buildAiTasks(userId);
        validateUniqueTaskIds(aiTasks);

        ReplanRequest aiRequest = ReplanRequest.builder()
                .requestId("replan-user-" + userId + "-" + UUID.randomUUID())
                .userId(userId)
                .weekStartDate(weekStartDate.toString())
                .weekEndDate(weekEndDate.toString())
                .timezone(TIMEZONE)
                .replanFromTime(replanFromTime)
                .completedTaskIds(toStringIds(completedTaskIds))
                .postponedTaskIds(toStringIds(postponedTaskIds))
                .tasks(aiTasks)
                .fixedSchedules(buildAiFixedSchedules(userId))
                .existingSchedules(buildExistingSchedules(userId))
                .build();

        AiScheduleResponse response = postToAi(AI_REPLAN_URL, aiRequest);

        /*
         * schedules만 저장하면 locked 상태로 preservedSchedules에 들어온
         * 기존 생성 일정이 DB에서 사라질 수 있다.
         * finalSchedules가 있으면 이를 사용하고, 없으면
         * schedules + preservedSchedules를 합친 뒤 FIXED 일정을 제외한다.
         */
        List<AiScheduleBlockResponse> generatedBlocks =
                extractGeneratedBlocks(response);

        log.info(
                "AI 일정 재배치 성공: 최종 생성 일정 {}개, 변경사항 {}개, 미배치 작업 {}개",
                generatedBlocks.size(),
                sizeOf(response.getChanges()),
                sizeOf(response.getUnscheduledTasks())
        );

        saveGeneratedSchedules(generatedBlocks, userId);
        logAiWarnings(response);
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
            case 3: // 상
                return 5;
            case 2: // 중
                return 3;
            case 1: // 하
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

        /*
         * preservedSchedules/finalSchedules에는 fixed 일정도 포함될 수 있으므로
         * taskId가 있는 일정만 GeneratedSchedule 테이블에 저장한다.
         * blockId를 기준으로 중복도 제거한다.
         */
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
        // 1. 기존 일정 중 '완료된(completed = true)' 것들의 blockId만 가볍게 추출해 둔다.
        Set<String> completedBlockIds = generatedRepository.findByUserId(userId)
                .stream()
                .filter(schedule -> Boolean.TRUE.equals(schedule.getCompleted()))
                .map(GeneratedSchedule::getBlockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 만약 완료된 일정이 있다면, 그 일정들의 엔티티도 따로 조회해서 살려둘 준비를 한다.
        List<GeneratedSchedule> completedSchedulesBackup = generatedRepository.findByUserId(userId)
                .stream()
                .filter(schedule -> Boolean.TRUE.equals(schedule.getCompleted()))
                .toList();

        /*
         * 결과가 비어 있어도 기존 일정을 먼저 지워야 한다.
         * 그렇지 않으면 AI가 모든 작업을 미배치한 경우 옛 일정이 남는다.
         */
        generatedRepository.deleteByUserId(userId);

        // [핵심 세이프가드 추가] Task별로 유저가 요청한 desiredSteps(목표 단계 수)를 가져와서 정확히 그 개수만큼만 limit 걸기
        List<Task> userTasks = taskRepository.findByUserId(userId);
        Map<Long, Integer> taskDesiredStepsMap = userTasks.stream()
                .collect(Collectors.toMap(Task::getTaskId, Task::getDesiredSteps, (existing, replacement) -> existing));

        List<AiScheduleBlockResponse> controlledAiResponse = new ArrayList<>();
        if (aiResponse != null && !aiResponse.isEmpty()) {
            // taskId별로 블록들을 그룹화
            Map<String, List<AiScheduleBlockResponse>> groupedByTask = aiResponse.stream()
                    .filter(block -> block.getTaskId() != null && !block.getTaskId().isBlank())
                    .collect(Collectors.groupingBy(AiScheduleBlockResponse::getTaskId));

            for (Map.Entry<String, List<AiScheduleBlockResponse>> entry : groupedByTask.entrySet()) {
                String taskIdStr = entry.getKey();
                List<AiScheduleBlockResponse> blocks = entry.getValue();
                Long taskId = parseLongTaskId(taskIdStr);

                // stepOrder 순서대로 정렬
                blocks.sort(Comparator.comparing(b -> b.getStepOrder() != null ? b.getStepOrder() : 0));

                // 해당 Task의 desiredSteps 가져오기 (없으면 제한 없이 다 허용)
                Integer targetSteps = taskId != null ? taskDesiredStepsMap.get(taskId) : null;

                if (targetSteps != null && targetSteps > 0 && blocks.size() > targetSteps) {
                    log.warn("AI가 요청된 단계 수({})를 초과하여 {}개의 블록을 생성했습니다. taskId={} 블록을 limit({})하여 잘라냅니다.",
                            targetSteps, blocks.size(), taskId, targetSteps);
                    blocks = blocks.stream().limit(targetSteps).collect(Collectors.toList());
                }
                controlledAiResponse.addAll(blocks);
            }

            // taskId가 없는 블록(일반 블록 등)도 그대로 포함
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
                                finalTitle = "[" + task.getTitle() + "] " + block.getTitle();
                            }
                        }

                        boolean isCompleted = Boolean.TRUE.equals(block.getCompleted())
                                || completedBlockIds.contains(block.getBlockId());

                        LocalDateTime finalStartTime = parseDateTime(block.getStartTime(), "startTime");
                        LocalDateTime finalEndTime = parseDateTime(block.getEndTime(), "endTime");

                        if (isCompleted) {
                            GeneratedSchedule oldSchedule = completedSchedulesBackup.stream()
                                    .filter(s -> (s.getBlockId() != null && s.getBlockId().equals(block.getBlockId()))
                                            || (s.getTaskId() != null && s.getTaskId().equals(parsedTaskId)))
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

        // AI 응답 목록에 포함되지 않은 기존 완료 일정들은 새로 빌드해서 안전하게 추가 (영속성 충돌 방지)
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }
}