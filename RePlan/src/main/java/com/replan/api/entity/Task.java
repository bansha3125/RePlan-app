package com.replan.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    private Integer estimatedMinutes;

    private LocalDateTime deadline;

    private Integer priority;

    private Boolean isCompleted = false;

    private boolean useAiDecomposition;
    private int desiredSteps;
}