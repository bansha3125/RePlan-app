package com.replan.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * AI generate/replan 공통 응답 DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiScheduleResponse {
    private Boolean success;
    private String message;
    private String requestId;
    private Long userId;
    private String weekStartDate;
    private String weekEndDate;
    private String timezone;
    private Boolean duplicateRequest;

    private List<AiScheduleBlockResponse> schedules = new ArrayList<>();
    private List<AiScheduleBlockResponse> preservedSchedules = new ArrayList<>();
    private List<AiScheduleBlockResponse> finalSchedules = new ArrayList<>();
    private List<AiUnscheduledTaskResponse> unscheduledTasks = new ArrayList<>();
    private List<AiScheduleChangeResponse> changes = new ArrayList<>();
    private List<AiWarningResponse> warnings = new ArrayList<>();

    private Map<String, Object> scores;
    private AiScheduleSummaryResponse summary;
}