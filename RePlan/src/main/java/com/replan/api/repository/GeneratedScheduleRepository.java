package com.replan.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.replan.api.entity.GeneratedSchedule;

import java.util.List;

public interface GeneratedScheduleRepository extends JpaRepository<GeneratedSchedule, Long> {
    List<GeneratedSchedule> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}