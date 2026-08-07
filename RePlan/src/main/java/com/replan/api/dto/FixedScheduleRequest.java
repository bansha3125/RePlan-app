package com.replan.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프론트엔드에서 고정 일정을 생성할 때 사용하는 요청 DTO.
 *
 * AI 서버로 전송할 때는 AiFixedScheduleRequest로 변환한다.
 */
@Getter
@NoArgsConstructor
public class FixedScheduleRequest {
    private Long userId;
    private String title;
    private String startTime;
    private String endTime;
    private boolean repeat;
    private String repeatDay;
}
