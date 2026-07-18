package com.replan.api.controller;

import com.replan.api.dto.FixedScheduleRequest;
import com.replan.api.dto.TaskRequest;
import com.replan.api.dto.WeeklyScheduleResponse;
import com.replan.api.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor // 생성자 주입을 위해 추가!
public class ScheduleController {

    private final ScheduleService scheduleService; // 서비스 주입

    @GetMapping("/weekly")
    public WeeklyScheduleResponse getWeeklySchedule(@RequestParam Long userId) {
        // DB조회 및 데이터 할당
        return scheduleService.getWeeklySchedules(userId);
    }

    @PostMapping("/fixed-schedules")
    public String addFixedSchedule(@RequestBody FixedScheduleRequest request) {
        scheduleService.saveFixedSchedule(request); //
        return "고정 일정 저장 성공!";
    }

    @PostMapping("/tasks")
    public String addTask(@RequestBody TaskRequest request) {
        scheduleService.saveTask(request);
        return "할 일 저장 성공!";
    }
}