package com.replan.api.service;

import com.replan.api.dto.FixedScheduleDto;
import com.replan.api.dto.FixedScheduleRequest;
import com.replan.api.dto.TaskRequest;
import com.replan.api.dto.WeeklyScheduleResponse;
import com.replan.api.entity.FixedSchedule;
import com.replan.api.entity.GeneratedSchedule;
import com.replan.api.entity.Task;
import com.replan.api.repository.FixedScheduleRepository;
import com.replan.api.repository.GeneratedScheduleRepository;
import com.replan.api.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final FixedScheduleRepository fixedRepository;
    private final GeneratedScheduleRepository generatedRepository;
    private final TaskRepository taskRepository;
    private final RestTemplate restTemplate;

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    // 1. 주간 스케줄 조회
    public WeeklyScheduleResponse getWeeklySchedules(Long userId) {
        // [수정됨] findAll() -> findByUserId(userId)로 변경하여 내 일정만 조회!
        List<FixedSchedule> fixedList = fixedRepository.findByUserId(userId);

        List<FixedScheduleDto> fixedDtos = fixedList.stream()
                .map(f -> FixedScheduleDto.builder()
                        .title(f.getTitle())
                        .startTime(f.getStartTime().toString())
                        .endTime(f.getEndTime().toString())
                        .repeatDay(f.getRepeatDay())
                        .build())
                .toList();

        return WeeklyScheduleResponse.builder()
                .fixedSchedules(fixedDtos)
                .generatedSchedules(new ArrayList<>()) // AI 일정은 아직 조회 로직 미구현이라 빈 리스트로 둠
                .build();
    }

    // 2. 고정 일정 저장
    public void saveFixedSchedule(FixedScheduleRequest request) {
        FixedSchedule schedule = FixedSchedule.builder()
                .userId(request.getUserId())
                .title(request.getTitle())
                .startTime(LocalDateTime.parse(request.getStartTime()))
                .endTime(LocalDateTime.parse(request.getEndTime()))
                .repeatDay(request.getRepeatDay())
                .build();
        fixedRepository.save(schedule);
    }

    // 3. 할 일 저장
    public void saveTask(TaskRequest request) {
        Task task = Task.builder()
                .userId(request.getUserId())
                .title(request.getTitle())
                .deadline(LocalDateTime.parse(request.getDeadline()))
                .estimatedMinutes(request.getEstimatedMinutes())
                .useAiDecomposition(request.isUseAiDecomposition())
                .desiredSteps(request.getDesiredSteps())
                .build();
        taskRepository.save(task);
    }

    // 4. AI 생성 로직
    public void generateAiSchedule(Long userId) {
        List<Task> tasks = taskRepository.findByUserId(userId);
        List<FixedSchedule> fixedSchedules = fixedRepository.findByUserId(userId);

        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("tasks", tasks);
        aiRequest.put("fixedSchedules", fixedSchedules);

        String aiServerUrl = "http://localhost:5000/ai/schedules/generate";

        try {
            List<Map<String, Object>> aiResponse = restTemplate.exchange(
                    aiServerUrl,
                    HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(aiRequest),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            ).getBody();

            log.info("AI 서버 응답 결과: {}", aiResponse);
            saveGeneratedSchedules(aiResponse, userId);

        } catch (Exception e) {
            log.error("AI 서버 통신 중 에러 발생: {}", e.getMessage());
        }
    }

    private void saveGeneratedSchedules(List<Map<String, Object>> aiResponse, Long userId) {
        if (aiResponse == null || aiResponse.isEmpty()) return;

        List<GeneratedSchedule> schedulesToSave = new ArrayList<>();

        for (Map<String, Object> scheduleMap : aiResponse) {
            GeneratedSchedule generatedSchedule = GeneratedSchedule.builder()
                    .userId(userId)
                    .title((String) scheduleMap.get("title"))
                    .startTime(LocalDateTime.parse((String) scheduleMap.get("startTime")))
                    .endTime(LocalDateTime.parse((String) scheduleMap.get("endTime")))
                    .build();

            schedulesToSave.add(generatedSchedule);
        }

        generatedRepository.saveAll(schedulesToSave);
    }
}