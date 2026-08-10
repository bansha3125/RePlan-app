package com.replan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 백엔드가 AI 서버의 tasks 배열로 전송하는 DTO.
 *
 * AI 경계에서는 taskId를 String으로 통일한다.
 * 백엔드 DB의 Long ID는 String.valueOf(task.getId())로 변환한다.
 */
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
