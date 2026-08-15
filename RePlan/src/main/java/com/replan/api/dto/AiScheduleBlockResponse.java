package com.replan.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiScheduleBlockResponse {
    private String blockId;
    private String taskId;
    private String title;
    private Integer stepOrder;
    private String startTime;
    private String endTime;
    private String source;
    private Boolean locked;
    private Boolean completed;
    private String reasonCode;
    private String reason;
}