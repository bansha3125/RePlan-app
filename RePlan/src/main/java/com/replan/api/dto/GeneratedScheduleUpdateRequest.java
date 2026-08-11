package com.replan.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GeneratedScheduleUpdateRequest {
    private Boolean locked;
    private Boolean completed;
    private String startTime;
    private String endTime;
}