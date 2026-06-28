package com.replan.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.replan.api.entity.GeneratedSchedule;

public interface GeneratedScheduleRepository extends JpaRepository<GeneratedSchedule, Long> {
}