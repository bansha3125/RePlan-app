package com.replan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplanRequest {
    private String requestId;
    private Long userId;
    private String weekStartDate;
    private String weekEndDate;

    @Builder.Default
    private String timezone = "Asia/Seoul";

    private String replanFromTime;

    @Builder.Default
    private List<String> completedTaskIds = new ArrayList<>();

    @Builder.Default
    private List<String> postponedTaskIds = new ArrayList<>();

    @Builder.Default
    private List<String> postponedBlockIds = new ArrayList<>();

    @Builder.Default
    private List<AiTaskRequest> tasks = new ArrayList<>();

    @Builder.Default
    private List<AiFixedScheduleRequest> fixedSchedules = new ArrayList<>();

    @Builder.Default
    private List<AiExistingScheduleRequest> existingSchedules = new ArrayList<>();
}