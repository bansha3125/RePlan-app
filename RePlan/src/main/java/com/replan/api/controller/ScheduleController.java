package com.replan.api.controller;

import com.replan.api.dto.*;
import com.replan.api.entity.FixedSchedule;
import com.replan.api.entity.Task;
import com.replan.api.entity.User;
import com.replan.api.resolver.CurrentDevice;
import com.replan.api.repository.FixedScheduleRepository;
import com.replan.api.repository.TaskRepository;
import com.replan.api.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final TaskRepository taskRepository;
    private final FixedScheduleRepository fixedRepository;

    @GetMapping("/weekly")
    public ResponseEntity<WeeklyScheduleResponse> getWeeklySchedules(
            @CurrentDevice User user,
            @RequestParam(required = false) String weekStartDate
    ) {
        WeeklyScheduleResponse response = scheduleService.getWeeklySchedules(user.getId(), weekStartDate);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/fixed-schedules")
    public String addFixedSchedule(
            @CurrentDevice User user,
            @RequestBody FixedScheduleRequest request
    ) {
        request.setUserId(user.getId());
        scheduleService.saveFixedSchedule(request);
        return "고정 일정 저장 성공!";
    }

    @GetMapping("/fixed-schedules")
    public List<FixedSchedule> getFixedSchedules(@CurrentDevice User user) {
        return fixedRepository.findByUserId(user.getId());
    }

    @PostMapping("/tasks")
    public String addTask(
            @CurrentDevice User user,
            @RequestBody TaskRequest request
    ) {
        request.setUserId(user.getId());
        scheduleService.saveTask(request);
        return "할 일 저장 성공!";
    }

    @GetMapping("/tasks")
    public List<TaskResponse> getTasks(
            @CurrentDevice User user,
            @RequestParam(required = false) String weekStartDate
    ) {
        return scheduleService.getTasks(user.getId(), weekStartDate);
    }
    @PatchMapping("/tasks/{taskId}")
    public ResponseEntity<Void> updateTask(
            @PathVariable Long taskId,
            @RequestBody TaskRequest request
    ) {
        scheduleService.updateTask(taskId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long taskId,
            @CurrentDevice User user
    ) {
        scheduleService.deleteTask(taskId, user.getId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/blocks/{blockId}")
    public ResponseEntity<Map<String, String>> updateGeneratedSchedule(
            @PathVariable String blockId,
            @RequestBody java.util.Map<String, Object> requestBody
    ) {
        Boolean locked = requestBody.get("locked") != null ? (Boolean) requestBody.get("locked") : null;
        Boolean completed = requestBody.get("completed") != null ? (Boolean) requestBody.get("completed") : null;
        String startTime = requestBody.get("startTime") != null ? requestBody.get("startTime").toString() : null;
        String endTime = requestBody.get("endTime") != null ? requestBody.get("endTime").toString() : null;

        scheduleService.updateGeneratedSchedule(blockId, locked, completed, startTime, endTime);

        return ResponseEntity.ok(Map.of("message", "일정 위치 및 상태가 성공적으로 변경되었습니다."));
    }

    @PostMapping("/generate")
    public String generateAiSchedule(
            @CurrentDevice User user,
            @RequestBody java.util.Map<String, Object> requestBody
    ) {
        String weekStartDate = requestBody.get("weekStartDate") != null
                ? requestBody.get("weekStartDate").toString()
                : null;

        scheduleService.generateAiSchedule(user.getId(), weekStartDate);
        return "AI 스케줄 생성 및 DB 저장 요청 완료!";
    }

    @PostMapping("/replan")
    public String replanAiSchedule(
            @CurrentDevice User user,
            @RequestBody java.util.Map<String, Object> requestBody
    ) {
        String replanFromTime = (String) requestBody.get("replanFromTime");

        @SuppressWarnings("unchecked")
        List<Long> completedTaskIds = requestBody.get("completedTaskIds") != null ?
                ((List<?>) requestBody.get("completedTaskIds")).stream().map(obj -> Long.valueOf(obj.toString())).toList() : List.of();

        @SuppressWarnings("unchecked")
        List<Long> postponedTaskIds = requestBody.get("postponedTaskIds") != null ?
                ((List<?>) requestBody.get("postponedTaskIds")).stream().map(obj -> Long.valueOf(obj.toString())).toList() : List.of();

        scheduleService.replanAiSchedule(user.getId(), replanFromTime, completedTaskIds, postponedTaskIds);
        return "AI 일정 재배치 및 DB 반영 요청 완료!";
    }
}