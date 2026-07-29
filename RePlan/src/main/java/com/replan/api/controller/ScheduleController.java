package com.replan.api.controller;

import com.replan.api.dto.FixedScheduleRequest;
import com.replan.api.dto.TaskRequest;
import com.replan.api.dto.WeeklyScheduleResponse;
import com.replan.api.entity.FixedSchedule;
import com.replan.api.entity.Task;
import com.replan.api.repository.FixedScheduleRepository;
import com.replan.api.repository.TaskRepository;
import com.replan.api.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final TaskRepository taskRepository;
    private final FixedScheduleRepository fixedRepository;

    @GetMapping("/weekly")
    public WeeklyScheduleResponse getWeeklySchedule(@RequestParam Long userId) {
        return scheduleService.getWeeklySchedules(userId);
    }

    @PostMapping("/fixed-schedules")
    public String addFixedSchedule(@RequestBody FixedScheduleRequest request) {
        scheduleService.saveFixedSchedule(request);
        return "고정 일정 저장 성공!";
    }

    @GetMapping("/fixed-schedules")
    public List<FixedSchedule> getFixedSchedules(@RequestParam Long userId) {
        return fixedRepository.findByUserId(userId);
    }

    @PostMapping("/tasks")
    public String addTask(@RequestBody TaskRequest request) {
        scheduleService.saveTask(request);
        return "할 일 저장 성공!";
    }

    @GetMapping("/tasks")
    public List<Task> getTasks(@RequestParam Long userId) {
        return taskRepository.findByUserId(userId);
    }

    @PostMapping("/generate")
    public String generateAiSchedule(@RequestParam Long userId) {
        scheduleService.generateAiSchedule(userId);
        return "AI 스케줄 생성 및 DB 저장 요청 완료!";
    }
}