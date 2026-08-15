package com.replan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFixedScheduleRequest {
    private Long fixedScheduleId;
    private String title;
    private String startTime;
    private String endTime;
}