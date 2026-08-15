package com.replan.api.dto;

import lombok.Builder;
import lombok.Getter;

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