package com.replan.api.controller;

import com.replan.api.dto.WeeklyScheduleResponse;
import com.replan.api.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}