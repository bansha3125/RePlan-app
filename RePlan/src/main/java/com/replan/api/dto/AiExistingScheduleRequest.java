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
public class AiExistingScheduleRequest {
    private String blockId;
    private String taskId;
    private String title;
    private Integer stepOrder;
    private String startTime;
    private String endTime;
    private String source;

    @Builder.Default
    private Boolean locked = false;

    @Builder.Default
    private Boolean completed = false;

    private String reasonCode;
    private String reason;
}