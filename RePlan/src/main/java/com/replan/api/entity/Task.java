package com.replan.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    private Integer estimatedTime; // 분 단위

    private LocalDateTime deadline;

    private Integer priority; // 1~5

    private Boolean isCompleted = false;
}