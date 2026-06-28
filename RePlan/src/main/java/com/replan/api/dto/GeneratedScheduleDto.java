package com.replan.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GeneratedScheduleDto {
    private String taskTitle;
    private String startTime;
    private String endTime;
    private Boolean isPinned;  // 고정 유무
}