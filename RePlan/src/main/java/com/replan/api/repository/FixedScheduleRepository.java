package com.replan.api.repository;

import com.replan.api.entity.FixedSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixedScheduleRepository extends JpaRepository<FixedSchedule, Long> {
    List<FixedSchedule> findByUserId(Long userId);
}