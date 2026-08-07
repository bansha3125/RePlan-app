package com.replan.api.dto;

/**
 * AI 응답 DTO를 프론트 응답 DTO로 변환한다.
 */
public final class AiScheduleMapper {

    private AiScheduleMapper() {
    }

    public static GeneratedScheduleDto toGeneratedScheduleDto(
            AiScheduleBlockResponse source
    ) {
        if (source == null) {
            throw new IllegalArgumentException("AI schedule response must not be null.");
        }

        return GeneratedScheduleDto.builder()
                .blockId(source.getBlockId())
                .taskId(toLongTaskId(source.getTaskId()))
                .title(source.getTitle())
                .stepOrder(source.getStepOrder())
                .startTime(source.getStartTime())
                .endTime(source.getEndTime())
                .source(source.getSource())
                .locked(Boolean.TRUE.equals(source.getLocked()))
                .completed(Boolean.TRUE.equals(source.getCompleted()))
                .reasonCode(source.getReasonCode())
                .reason(source.getReason())
                .build();
    }

    private static Long toLongTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(taskId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "AI가 반환한 taskId가 백엔드 Long ID 형식이 아닙니다: " + taskId,
                    exception
            );
        }
    }
}
