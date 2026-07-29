package com.replan.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FixedScheduleRequest {
    private Long userId;
    private String title;
    private String startTime;
    private String endTime;
    private boolean repeat;
    private String repeatDay;
}