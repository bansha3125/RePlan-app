package com.replan.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class WeeklyScheduleResponse {

    @Builder.Default
    private List<FixedScheduleDto> fixedSchedules = new ArrayList<>();

    @Builder.Default
    private List<GeneratedScheduleDto> generatedSchedules = new ArrayList<>();
}