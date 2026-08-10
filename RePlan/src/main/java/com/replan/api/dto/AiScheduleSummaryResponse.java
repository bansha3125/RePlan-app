package com.replan.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * generate와 replan에서 사용 가능한 모든 count 필드를 포함한다.
 * 응답에 없는 필드는 null로 유지된다.
 */
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