package com.replan.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.replan.api.entity.Task;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByUserId(Long userId);
}