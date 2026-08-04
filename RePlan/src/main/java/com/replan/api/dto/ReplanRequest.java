package com.replan.api.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ReplanRequest {
    private String requestId;
    private Long userId;
    private String weekStartDate;
    private String weekEndDate;
    private String timezone;
    private String replanFromTime;

    private List<Long> completedTaskIds;
    private List<Long> postponedTaskIds;

    private List<Object> tasks;
    private List<Object> fixedSchedules;
    private List<Object> existingSchedules;
}