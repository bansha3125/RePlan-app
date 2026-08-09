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

    public WeeklyScheduleResponse getWeeklySchedules(Long userId) {
        List<FixedScheduleDto> fixedDtos = fixedRepository.findByUserId(userId)
                .stream()
                .map(f -> FixedScheduleDto.builder()
                        .fixedScheduleId(f.getFixedScheduleId())
                        .title(f.getTitle())
                        .startTime(f.getStartTime().toString())
                        .endTime(f.getEndTime().toString())
                        .repeatDay(f.getRepeatDay())
                        .locked(true)
                        .build())
                .toList();

        List<GeneratedScheduleDto> generatedDtos =
                generatedRepository.findByUserId(userId)
                        .stream()
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
                .useAiDecomposition(request.isUseAiDecomposition())
                .desiredSteps(request.getDesiredSteps())
                .priority(request.getPriority() != null ? request.getPriority() : 2) // [수정] 전달받은 중요도 저장 (없으면 기본값 2)
                .build());
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

        log.info(
                "AI 스케줄 생성 성공: 생성 일정 {}개, 미배치 작업 {}개",
                generatedBlocks.size(),
                sizeOf(response.getUnscheduledTasks())
        );

        saveGeneratedSchedules(generatedBlocks, userId);
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
                    // [추가] 프론트/DB의 3단계(상중하 또는 1,2,3)를 AI가 요구하는 1~5 단계로 매핑
                    int mappedPriority = convertPriorityToAiScale(task.getPriority());

                    return AiTaskRequest.builder()
                            .taskId(String.valueOf(task.getTaskId()))
                            .title(task.getTitle())
                            .estimatedMinutes(task.getEstimatedMinutes())
                            .deadline(task.getDeadline().toString())

                        /*
                         * AI 명세상 필수값이다.
                         * 현재 TaskRequest/Task Entity에 연결된 필드가 없어 임시 기본값 사용.
                         */
                            .priority(mappedPriority) // [수정] 변환된 1~5 값 전달

                            .difficulty(task.getDifficulty())
                            .focusRequired(task.getFocusRequired())
                            .postponeCount(task.getPostponeCount())
                            .completedMinutes(task.getCompletedMinutes())
                            .remainingMinutes(task.getEstimatedMinutes())
                            .completed(task.isCompleted())
                            .prerequisiteTaskIds(new ArrayList<>())
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
                return frontPriority; // 이미 1~5 범위라면 그대로 반환
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
        /*
         * 결과가 비어 있어도 기존 일정을 먼저 지워야 한다.
         * 그렇지 않으면 AI가 모든 작업을 미배치한 경우 옛 일정이 남는다.
         */
        generatedRepository.deleteByUserId(userId);

        if (aiResponse == null || aiResponse.isEmpty()) {
            return;
        }

        List<GeneratedSchedule> schedules = aiResponse.stream()
                .map(block -> GeneratedSchedule.builder()
                        .userId(userId)
                        .taskId(parseLongTaskId(block.getTaskId()))
                        .title(block.getTitle())
                        .startTime(parseDateTime(block.getStartTime(), "startTime"))
                        .endTime(parseDateTime(block.getEndTime(), "endTime"))
                        .blockId(block.getBlockId())
                        .stepOrder(block.getStepOrder())
                        .source(block.getSource())
                        .locked(Boolean.TRUE.equals(block.getLocked()))
                        .completed(Boolean.TRUE.equals(block.getCompleted()))
                        .reasonCode(block.getReasonCode())
                        .reason(block.getReason())
                        .build())
                .toList();

        generatedRepository.saveAll(schedules);
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