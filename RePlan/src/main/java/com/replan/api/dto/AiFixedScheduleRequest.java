package com.replan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * 백엔드가 AI 서버의 fixedSchedules 배열로 전송하는 DTO.
 *
 * repeatDay와 locked는 AI 최초 생성 요청 규격에 없으므로 포함하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFixedScheduleRequest {
    private Long fixedScheduleId;
    private String title;
    private String startTime;
    private String endTime;
}