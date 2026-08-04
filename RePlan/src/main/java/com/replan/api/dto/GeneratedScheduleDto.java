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
    private boolean locked;
    private boolean completed;
    private String reasonCode;
    private String reason;
}