package com.replan.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * 프론트엔드에서 일반 할 일을 생성할 때 사용하는 요청 DTO.
 *
 * 주의:
 * 이 클래스는 AI 스케줄링 요청의 tasks 항목과 목적이 다르므로
 * AiTaskRequest로 대체하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class TaskRequest {
    private Long userId;
    private String title;
    private String deadline;
    private Integer estimatedMinutes;

    @JsonProperty("useAiDecomposition") // 추가
    private Boolean useAiDecomposition;

    @JsonProperty("desiredSteps") // 추가
    private Integer desiredSteps;

    private String deadlineType;
    private Long linkedScheduleId;
    private Integer priority;
}