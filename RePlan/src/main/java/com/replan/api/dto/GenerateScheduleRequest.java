package com.replan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 백엔드가 AI 서버 POST /schedules/generate 로 전송하는 요청 DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateScheduleRequest {
    private String requestId;
    private Long userId;
    private String weekStartDate;
    private String weekEndDate;

    @Builder.Default
    private String timezone = "Asia/Seoul";

    @Builder.Default
    private List<AiTaskRequest> tasks = new ArrayList<>();

    @Builder.Default
    private List<AiFixedScheduleRequest> fixedSchedules = new ArrayList<>();

    @Builder.Default
    private List<AiExistingScheduleRequest> existingSchedules = new ArrayList<>();
}