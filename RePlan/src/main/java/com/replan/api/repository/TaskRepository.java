package com.replan.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.replan.api.entity.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {
}