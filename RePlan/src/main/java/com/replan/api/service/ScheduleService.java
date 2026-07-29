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

    private static final String AI_SERVER_URL = "http://localhost:5000/ai/schedules/generate";

    public WeeklyScheduleResponse getWeeklySchedules(Long userId) {
        // 1. 고정 일정 조회
        List<FixedScheduleDto> fixedDtos = fixedRepository.findByUserId(userId).stream()
                .map(f -> FixedScheduleDto.builder()
                        .title(f.getTitle())
                        .startTime(f.getStartTime().toString())
                        .endTime(f.getEndTime().toString())
                        .repeatDay(f.getRepeatDay())
                        .build())
                .toList();

        // 2. AI가 생성한 일정 조회
        List<GeneratedScheduleDto> generatedDtos = generatedRepository.findByUserId(userId).stream()
                .map(g -> GeneratedScheduleDto.builder()
                        .title(g.getTitle())
                        .startTime(g.getStartTime().toString())
                        .endTime(g.getEndTime().toString())
                        .build())
                .toList();

        return WeeklyScheduleResponse.builder()
                .fixedSchedules(fixedDtos)
                .generatedSchedules(generatedDtos) // DTO 리스트를 장착!
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
                .build());
    }

    public void generateAiSchedule(Long userId) {
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("tasks", taskRepository.findByUserId(userId));
        aiRequest.put("fixedSchedules", fixedRepository.findByUserId(userId));

        try {
            List<Map<String, Object>> aiResponse = restTemplate.exchange(
                    AI_SERVER_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(aiRequest),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            ).getBody();

            log.info("AI 스케줄 생성 성공, 결과 수신 완료");
            saveGeneratedSchedules(aiResponse, userId);

        } catch (Exception e) {
            log.error("AI 서버 통신 실패: {}", e.getMessage());
        }
    }

    private void saveGeneratedSchedules(List<Map<String, Object>> aiResponse, Long userId) {
        if (aiResponse == null || aiResponse.isEmpty()) return;

        List<GeneratedSchedule> schedules = aiResponse.stream()
                .map(map -> GeneratedSchedule.builder()
                        .userId(userId)
                        .title((String) map.get("title"))
                        .startTime(LocalDateTime.parse((String) map.get("startTime")))
                        .endTime(LocalDateTime.parse((String) map.get("endTime")))
                        .build())
                .toList();

        generatedRepository.saveAll(schedules);
    }
}