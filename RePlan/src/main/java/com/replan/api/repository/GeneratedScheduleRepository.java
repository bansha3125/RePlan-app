package com.replan.api.repository;

import com.replan.api.entity.GeneratedSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GeneratedScheduleRepository extends JpaRepository<GeneratedSchedule, Long> {

    List<GeneratedSchedule> findByUserId(Long userId);

    List<GeneratedSchedule> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);

    Optional<GeneratedSchedule> findByBlockId(String blockId);

    boolean existsByTaskIdAndCompletedFalse(Long taskId);

    void deleteByUserId(Long userId);

    void deleteByTaskId(Long taskId);
}