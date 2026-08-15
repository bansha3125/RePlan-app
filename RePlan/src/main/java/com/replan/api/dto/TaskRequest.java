package com.replan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TaskRequest {
    private Long userId;
    private String title;
    private String deadline;
    private Integer estimatedMinutes;

    @JsonProperty("useAiDecomposition")
    private Boolean useAiDecomposition;

    @JsonProperty("desiredSteps")
    private Integer desiredSteps;

    private String deadlineType;
    private Long linkedScheduleId;
    private Integer priority;
}