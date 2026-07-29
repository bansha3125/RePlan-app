package com.replan.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TaskRequest {
    private Long userId;
    private String title;
    private String deadline;
    private int estimatedMinutes;
    private boolean useAiDecomposition;
    private int desiredSteps;

    private String deadlineType;
    private Long linkedScheduleId;
}