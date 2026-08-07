package com.replan.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 백엔드가 프론트엔드에 반환하는 고정 일정 DTO.
 *
 * repeatDay와 locked는 앱 화면에 필요한 값이며,
 * AI 요청에는 AiFixedScheduleRequest를 별도로 사용한다.
 */
@Getter
@Builder
public class FixedScheduleDto {
    private Long fixedScheduleId;
    private String title;
    private String startTime;
    private String endTime;
    private String repeatDay;

    @Builder.Default
    private boolean locked = true;
}