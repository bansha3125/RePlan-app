package com.replan.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.replan.api.entity.FixedSchedule;

public interface FixedScheduleRepository extends JpaRepository<FixedSchedule, Long> {
}