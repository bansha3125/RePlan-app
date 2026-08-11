package com.replan.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.replan.api.entity.GeneratedSchedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GeneratedScheduleRepository extends JpaRepository<GeneratedSchedule, Long> {
    List<GeneratedSchedule> findByUserId(Long userId);
    void deleteByUserId(Long userId);
    void deleteByTaskId(Long taskId);

    Optional<GeneratedSchedule> findByBlockId(String blockId);
    List<GeneratedSchedule> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);

    boolean existsByTaskIdAndCompletedFalse(Long taskId);
}