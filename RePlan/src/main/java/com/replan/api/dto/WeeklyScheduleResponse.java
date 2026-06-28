package com.replan.api.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class WeeklyScheduleResponse {
    private List<FixedScheduleDto> fixedSchedules;
    private List<GeneratedScheduleDto> generatedSchedules;
}