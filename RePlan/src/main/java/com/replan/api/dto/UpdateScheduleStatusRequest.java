package com.replan.api.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class UpdateScheduleStatusRequest {
    private Long userId;
    private String replanFromTime;

    private String blockId;
    private String startTime;
    private String endTime;

    private List<Long> completedTaskIds;
    private List<Long> postponedTaskIds;
}