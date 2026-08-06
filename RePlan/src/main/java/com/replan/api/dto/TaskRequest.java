package com.replan.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TaskRequest {
    private Long userId;
    private String title;
    private Integer estimatedMinutes;
    private String deadline;
    private Integer priority;
    private Integer difficulty;
    private Integer focusRequired;
    private Integer desiredSteps;
    private String deadlineType;
    private Long linkedScheduleId;
    private boolean useAiDecomposition;
}