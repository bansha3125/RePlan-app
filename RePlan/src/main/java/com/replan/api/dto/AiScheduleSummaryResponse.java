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
public class AiScheduleSummaryResponse {
    private Integer createdCount;
    private Integer splitCount;
    private Integer movedCount;
    private Integer keptCount;
    private Integer removedCount;
    private Integer preservedCount;
    private Integer unscheduledCount;
    private Integer warningCount;
}