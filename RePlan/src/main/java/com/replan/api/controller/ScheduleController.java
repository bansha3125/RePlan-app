package com.replan.api.controller;

import com.replan.api.dto.FixedScheduleRequest;
import com.replan.api.dto.TaskRequest;
import com.replan.api.dto.TaskResponse;
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
    public ResponseEntity<WeeklyScheduleResponse> getWeeklySchedules(
            @RequestParam Long userId,
            @RequestParam(required = false) String weekStartDate
    ) {
        WeeklyScheduleResponse response = scheduleService.getWeeklySchedules(userId, weekStartDate);
        return ResponseEntity.ok(response);
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
    public List<TaskResponse> getTasks(@RequestParam Long userId) {
        return scheduleService.getTasks(userId);
    }

    @PatchMapping("/tasks/{taskId}/complete")
    public String updateTaskCompletion(
            @PathVariable Long taskId,
            @RequestParam boolean completed
    ) {
        scheduleService.updateTaskCompletion(taskId, completed);
        return "Task 완료 상태 변경 성공!";
    }

    @PatchMapping("/generated/{blockId}")
    public ResponseEntity<Void> updateGeneratedSchedule(
            @PathVariable String blockId,
            @RequestParam(required = false) Boolean locked,
            @RequestParam(required = false) Boolean completed
    ) {
        scheduleService.updateGeneratedSchedule(blockId, locked, completed);
        return ResponseEntity.ok().build();
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
            @RequestParam Long userId
    ) {
        scheduleService.deleteTask(taskId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/generate")
    public String generateAiSchedule(@RequestBody java.util.Map<String, Object> requestBody) {
        Object userIdObj = requestBody.get("userId");
        if (userIdObj == null) {
            throw new IllegalArgumentException("요청 본문에 userId가 포함되어 있지 않습니다.");
        }
        Long userId = Long.valueOf(userIdObj.toString());

        String weekStartDate = requestBody.get("weekStartDate") != null
                ? requestBody.get("weekStartDate").toString()
                : null;

        scheduleService.generateAiSchedule(userId, weekStartDate);
        return "AI 스케줄 생성 및 DB 저장 요청 완료!";
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
}