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
public class AiScheduleChangeResponse {
    private Integer sequence;
    private String action;
    private String taskId;
    private String blockId;
    private String title;
    private String beforeStartTime;
    private String beforeEndTime;
    private String afterStartTime;
    private String afterEndTime;
    private String reasonCode;
    private String reason;
}