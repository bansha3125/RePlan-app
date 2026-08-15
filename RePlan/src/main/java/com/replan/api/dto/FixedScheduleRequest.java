package com.replan.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FixedScheduleRequest {
    private Long userId;
    private String title;
    private String startTime;
    private String endTime;
    private boolean repeat;
    private String repeatDay;
}