package com.replan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 백엔드가 AI 서버 POST /ai/schedules/replan 로 전송하는 요청 DTO.
 *
 * 기존 List<Object>를 실제 규격 타입으로 교체했다.
 */
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
    private List<AiTaskRequest> tasks = new ArrayList<>();

    @Builder.Default
    private List<AiFixedScheduleRequest> fixedSchedules = new ArrayList<>();

    @Builder.Default
    private List<AiExistingScheduleRequest> existingSchedules = new ArrayList<>();
}
