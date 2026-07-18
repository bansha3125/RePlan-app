package com.replan.api.service;

import com.replan.api.dto.FixedScheduleDto;
import com.replan.api.dto.FixedScheduleRequest;
import com.replan.api.dto.TaskRequest;
import com.replan.api.dto.WeeklyScheduleResponse;
import com.replan.api.entity.FixedSchedule;
import com.replan.api.entity.Task;
import com.replan.api.repository.FixedScheduleRepository;
import com.replan.api.repository.GeneratedScheduleRepository;
import com.replan.api.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final FixedScheduleRepository fixedRepository;
    private final GeneratedScheduleRepository generatedRepository;
    private final TaskRepository taskRepository;

    // 1. 주간 스케줄 조회
    public WeeklyScheduleResponse getWeeklySchedules(Long userId) {
        List<FixedSchedule> fixedList = fixedRepository.findAll();
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
                .generatedSchedules(new ArrayList<>())
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

        System.out.println("조회된 태스크 개수: " + tasks.size());
    }
}