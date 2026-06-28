package com.replan.api.service;

import com.replan.api.dto.FixedScheduleDto;
import com.replan.api.dto.WeeklyScheduleResponse;
import com.replan.api.entity.FixedSchedule;
import com.replan.api.repository.FixedScheduleRepository;
import com.replan.api.repository.GeneratedScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final FixedScheduleRepository fixedRepository;
    private final GeneratedScheduleRepository generatedRepository;

    public WeeklyScheduleResponse getWeeklySchedules(Long userId) {
        // 1. DB에서 해당 유저의 고정 일정을 싹 다 가져와!
        List<FixedSchedule> fixedList = fixedRepository.findAll();

        // 2. 가져온 엔티티들을 DTO(우리가 약속한 그릇)로 변환해줘야 해.
        List<FixedScheduleDto> fixedDtos = fixedList.stream()
                .map(f -> FixedScheduleDto.builder()
                        .title(f.getTitle())
                        .startTime(f.getStartTime().toString())
                        .endTime(f.getEndTime().toString())
                        .repeatDay(f.getRepeatDay())
                        .build())
                .toList();

        // 3. 응답에 담아서 보내!
        return WeeklyScheduleResponse.builder()
                .fixedSchedules(fixedDtos)
                .generatedSchedules(new ArrayList<>())
                .build();
    }
}