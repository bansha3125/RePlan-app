package com.replan.api.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private Long taskId;
    private String title;
    private String deadline;
    private int estimatedMinutes;
    private boolean completed;
    private Integer priority;

    private int desiredSteps;
}