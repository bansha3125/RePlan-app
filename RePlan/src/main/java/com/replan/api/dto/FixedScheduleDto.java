package com.replan.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FixedScheduleDto {
    private Long fixedScheduleId;
    private String title;
    private String startTime;
    private String endTime;
    private String repeatDay;

    @Builder.Default
    private boolean locked = true;
}