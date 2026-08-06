package com.replan.api.controller;

import com.replan.api.dto.FixedScheduleRequest;
import com.replan.api.dto.TaskRequest;
import com.replan.api.dto.UpdateScheduleStatusRequest;
import com.replan.api.dto.WeeklyScheduleResponse;
import com.replan.api.entity.FixedSchedule;
import com.replan.api.entity.Task;
import com.replan.api.repository.FixedScheduleRepository;
import com.replan.api.repository.TaskRepository;
import com.replan.api.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public WeeklyScheduleResponse getWeeklySchedule(
            @RequestParam Long userId,
            @RequestParam(required = false) String weekStartDate
    ) {
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
    public WeeklyScheduleResponse generateAiSchedule(@RequestParam("userId") Long userId) {
        scheduleService.generateAiSchedule(userId);
        return scheduleService.getWeeklySchedules(userId);
    }

    @PostMapping("/replan")
    public String replanAiSchedule(@RequestBody java.util.Map<String, Object> requestBody) {
        Long userId = Long.valueOf(requestBody.get("userId").toString());
        String replanFromTime = (String) requestBody.get("replanFromTime");

        @SuppressWarnings("unchecked")
        List<Long> completedTaskIds = requestBody.get("completedTaskIds") != null ?
                ((List<?>) requestBody.get("completedTaskIds")).stream().map(obj -> Long.valueOf(obj.toString())).toList() : List.of();

        @SuppressWarnings("unchecked")
        List<Long> postponedTaskIds = requestBody.get("postponedTaskIds") != null ?
                ((List<?>) requestBody.get("postponedTaskIds")).stream().map(obj -> Long.valueOf(obj.toString())).toList() : List.of();

        scheduleService.replanAiSchedule(userId, replanFromTime, completedTaskIds, postponedTaskIds);
        return "AI 일정 재배치 및 DB 반영 요청 완료!";
    }

    @PostMapping("/status")
    public ResponseEntity<WeeklyScheduleResponse> updateScheduleStatus(@RequestBody UpdateScheduleStatusRequest request) {
        Long userId = request.getUserId() != null ? request.getUserId() : 1L;

        // 1. 바꾼 개별 일정의 시간(startTime, endTime) DB에 반영
        scheduleService.updateScheduleTime(userId, request.getBlockId(), request.getStartTime(), request.getEndTime());

        // 2. 필요시 기존 AI 재배치도 실행
        if (request.getReplanFromTime() != null) {
            scheduleService.replanAiSchedule(
                    userId,
                    request.getReplanFromTime(),
                    request.getCompletedTaskIds(),
                    request.getPostponedTaskIds()
            );
        }

        // 3. 갱신된 최신 주간 일정 데이터 리턴
        WeeklyScheduleResponse response = scheduleService.getWeeklySchedules(userId);
        return ResponseEntity.ok(response);
    }
}