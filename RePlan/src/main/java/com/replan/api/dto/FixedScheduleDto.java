package com.replan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor

public class FixedScheduleDto {
    private String title;
    private String startTime;
    private String endTime;
    private String repeatDay;
}
