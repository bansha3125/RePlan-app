package com.replan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTaskRequest {
    private String taskId;
    private String title;
    private Integer estimatedMinutes;
    private String deadline;
    private Integer priority;

    private Integer difficulty;
    private Integer focusRequired;

    @Builder.Default
    private Integer postponeCount = 0;

    @Builder.Default
    private Integer completedMinutes = 0;

    private Integer remainingMinutes;

    @Builder.Default
    private Boolean completed = false;

    @Builder.Default
    private List<String> prerequisiteTaskIds = new ArrayList<>();

    private Boolean useAiDecomposition;
    private Integer desiredSteps;
}