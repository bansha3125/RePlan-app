package com.replan.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프론트엔드에서 일반 할 일을 생성할 때 사용하는 요청 DTO.
 *
 * 주의:
 * 이 클래스는 AI 스케줄링 요청의 tasks 항목과 목적이 다르므로
 * AiTaskRequest로 대체하지 않는다.
 */
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