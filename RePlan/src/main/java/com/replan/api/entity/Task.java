package com.replan.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    private Long userId;
    private String title;
    private LocalDateTime deadline;
    private int estimatedMinutes;
    private boolean useAiDecomposition;
    private int desiredSteps;
}