package com.replan.api.service;

import com.replan.api.dto.*;
import com.replan.api.entity.*;
import com.replan.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final FixedScheduleRepository fixedRepository;
    private final GeneratedScheduleRepository generatedRepository;
    private final TaskRepository taskRepository;
    private final RestTemplate restTemplate;

    private static final String AI_SERVER_URL = "http://127.0.0.1:8000/schedules/generate";
    private static final String AI_REPLAN_URL = "http://127.0.0.1:8000/ai/schedules/replan";

    public WeeklyScheduleResponse getWeeklySchedules(Long userId) {
        // 1. 고정 일정 DTO 매핑
        List<FixedScheduleDto> fixedDtos = fixedRepository.findByUserId(userId).stream()
                .map(f -> FixedScheduleDto.builder()
                        .fixedScheduleId(f.getFixedScheduleId())
                        .title(f.getTitle())
                        .startTime(f.getStartTime().toString())
                        .endTime(f.getEndTime().toString())
                        .repeatDay(f.getRepeatDay())
                        .locked(true) // 고정 일정은 기본 locked 처리
                        .build())
                .toList();

        // 2. AI 생성 일정 DTO 매핑
        List<GeneratedScheduleDto> generatedDtos = generatedRepository.findByUserId(userId).stream()
                .map(g -> GeneratedScheduleDto.builder()
                        .blockId(g.getBlockId())
                        .taskId(g.getTaskId())
                        .title(g.getTitle())
                        .stepOrder(g.getStepOrder())
                        .startTime(g.getStartTime().toString())
                        .endTime(g.getEndTime().toString())
                        .source(g.getSource())
                        .locked(g.getLocked() != null ? g.getLocked() : false)
                        .completed(g.getCompleted() != null ? g.getCompleted() : false)
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
        Long userId = (request.getUserId() != null) ? request.getUserId() : 1L;

        fixedRepository.save(FixedSchedule.builder()
                .userId(userId)
                .title(request.getTitle())
                .startTime(LocalDateTime.parse(request.getStartTime()))
                .endTime(LocalDateTime.parse(request.getEndTime()))
                .repeatDay(request.getRepeatDay())
                .build());
    }

    public void saveTask(TaskRequest request) {
        Long userId = (request.getUserId() != null) ? request.getUserId() : 1L;

        taskRepository.save(Task.builder()
                .userId(userId)
                .title(request.getTitle())
                .deadline(LocalDateTime.parse(request.getDeadline()))
                .estimatedMinutes(request.getEstimatedMinutes())
                .useAiDecomposition(request.isUseAiDecomposition())
                .desiredSteps(request.getDesiredSteps())
                .build());
    }

    @Transactional
    public void generateAiSchedule(Long userId) {
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("requestId", "generate-user-" + userId + "-" + System.currentTimeMillis());
        aiRequest.put("userId", userId);
        aiRequest.put("weekStartDate", "2026-08-03");
        aiRequest.put("weekEndDate", "2026-08-09");
        aiRequest.put("timezone", "Asia/Seoul");

        List<Task> userTasks = taskRepository.findByUserId(userId);
        aiRequest.put("tasks", userTasks);

        aiRequest.put("fixedSchedules", fixedRepository.findByUserId(userId));
        aiRequest.put("existingSchedules", List.of());

        try {
            // 상수로 선언한 URL 사용
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    AI_SERVER_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(aiRequest),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("schedules")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> aiResponseSchedules = (List<Map<String, Object>>) responseBody.get("schedules");

                log.info("AI 스케줄 생성 성공, 결과 수신 완료. 개수: {}", aiResponseSchedules.size());
                saveGeneratedSchedules(aiResponseSchedules, userId);
            }

        } catch (Exception e) {
            log.error("AI 서버 통신 실패: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void replanAiSchedule(Long userId, String replanFromTime, List<Long> completedTaskIds, List<Long> postponedTaskIds) {
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("requestId", "replan-user-" + userId + "-" + System.currentTimeMillis());
        aiRequest.put("userId", userId);
        aiRequest.put("weekStartDate", "2026-08-03");
        aiRequest.put("weekEndDate", "2026-08-09");
        aiRequest.put("timezone", "Asia/Seoul");
        aiRequest.put("replanFromTime", replanFromTime);

        aiRequest.put("completedTaskIds", completedTaskIds != null ? completedTaskIds : List.of());
        aiRequest.put("postponedTaskIds", postponedTaskIds != null ? postponedTaskIds : List.of());

        List<Task> userTasks = taskRepository.findByUserId(userId);
        Task latestTask = userTasks.isEmpty() ? null : userTasks.get(userTasks.size() - 1);
        aiRequest.put("tasks", latestTask != null ? List.of(latestTask) : List.of());
        aiRequest.put("fixedSchedules", fixedRepository.findByUserId(userId));

        aiRequest.put("existingSchedules", generatedRepository.findByUserId(userId));

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    AI_REPLAN_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(aiRequest),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && (Boolean.TRUE.equals(responseBody.get("success")) || responseBody.containsKey("schedules"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> aiResponseSchedules = (List<Map<String, Object>>) responseBody.get("schedules");

                log.info("AI 일정 재배치 성공, 결과 수신 완료. 개수: {}", aiResponseSchedules != null ? aiResponseSchedules.size() : 0);

                if (aiResponseSchedules != null) {
                    saveGeneratedSchedules(aiResponseSchedules, userId);
                }
            }

        } catch (Exception e) {
            log.error("AI 서버 재배치 통신 실패: {}", e.getMessage(), e);
        }
    }

    @Transactional
    private void saveGeneratedSchedules(List<Map<String, Object>> aiResponse, Long userId) {
        if (aiResponse == null || aiResponse.isEmpty()) return;

        log.info("AI 생성 일정 DB 반영 시작. 받은 개수: {}", aiResponse.size());

        generatedRepository.deleteByUserId(userId);
        generatedRepository.flush();

        List<GeneratedSchedule> schedules = aiResponse.stream()
                .map(map -> {
                    Object rawTaskId = map.get("taskId");
                    Long parsedTaskId = null;
                    if (rawTaskId != null) {
                        parsedTaskId = Long.valueOf(String.valueOf(rawTaskId));
                    }

                    log.info("📌 AI가 보내준 일정 타이틀 확인: {}", map.get("title"));

                    return GeneratedSchedule.builder()
                            .userId(userId)
                            .taskId(parsedTaskId)
                            .title((String) map.get("title"))
                            .startTime(LocalDateTime.parse((String) map.get("startTime")))
                            .endTime(LocalDateTime.parse((String) map.get("endTime")))
                            .blockId((String) map.get("blockId"))
                            .stepOrder(map.get("stepOrder") != null ? ((Number) map.get("stepOrder")).intValue() : null)
                            .source((String) map.get("source"))
                            .locked(map.get("locked") != null && (Boolean) map.get("locked"))
                            .completed(map.get("completed") != null && (Boolean) map.get("completed"))
                            .reasonCode((String) map.get("reasonCode"))
                            .reason((String) map.get("reason"))
                            .build();
                })
                .toList();

        generatedRepository.saveAll(schedules);
    }

    public void updateScheduleTime(Long userId, String blockId, String startTime, String endTime) {
        if (blockId == null || startTime == null || endTime == null) {
            log.warn("일정 시간 변경 실패: 필수 파라미터 누락 (blockId={}, startTime={}, endTime={})", blockId, startTime, endTime);
            return;
        }

        String cleanBlockId = blockId.startsWith("generated:") ? blockId.substring("generated:".length()) : blockId;

        log.info("일정 시간 변경 요청 수신 -> 원본 blockId: {}, 정제된 cleanBlockId: {}, start: {}, end: {}", blockId, cleanBlockId, startTime, endTime);

        // 1. AI 생성 일정(GeneratedSchedule) 찾기
        Optional<GeneratedSchedule> optionalSchedule = generatedRepository.findByBlockId(cleanBlockId);
        if (optionalSchedule.isEmpty()) {
            optionalSchedule = generatedRepository.findByBlockId(blockId);
        }

        if (optionalSchedule.isPresent()) {
            GeneratedSchedule schedule = optionalSchedule.get();
            schedule.setStartTime(LocalDateTime.parse(startTime));
            schedule.setEndTime(LocalDateTime.parse(endTime));
            generatedRepository.save(schedule);
            log.info("✨ 생성 일정 시간 DB 업데이트 완료: blockId={}, start={}, end={}", schedule.getBlockId(), startTime, endTime);
            return;
        }

        // 2. 고정 일정(FixedSchedule)인 경우
        try {
            Long fixedId = Long.valueOf(cleanBlockId);
            fixedRepository.findById(fixedId).ifPresent(schedule -> {
                schedule.setStartTime(LocalDateTime.parse(startTime));
                schedule.setEndTime(LocalDateTime.parse(endTime));
                fixedRepository.save(schedule);
                log.info("✨ 고정 일정 시간 DB 업데이트 완료: id={}, start={}, end={}", fixedId, startTime, endTime);
            });
        } catch (NumberFormatException e) {
            log.warn("매칭되는 일정을 찾을 수 없습니다. blockId={}", blockId);
        }
    }
}