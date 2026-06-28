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
public class GeneratedSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scheduleId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long taskId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Boolean isPinned = false; // 핵심: 드래그 앤 드롭 고정 여부
}