package com.replan.api.dto;

import lombok.Builder;
import lombok.Getter;

/*
 * 백엔드가 프론트엔드에 반환하는 AI 생성 일정 DTO.
 *
 * 프론트/DB에서는 Long taskId를 유지하고,
 * AI 응답의 String taskId는 AiScheduleMapper에서 Long으로 변환한다.
 */
@Getter
@Builder
public class GeneratedScheduleDto {
    private String blockId;
    private Long taskId;
    private String title;
    private Integer stepOrder;
    private String startTime;
    private String endTime;
    private String source;

    @Builder.Default
    private boolean locked = false;

    @Builder.Default
    private boolean completed = false;

    private String reasonCode;
    private String reason;
}