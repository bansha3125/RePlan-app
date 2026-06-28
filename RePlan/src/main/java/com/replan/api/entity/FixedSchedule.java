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
public class FixedSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fixedId;

    @Column(nullable = false)
    private Long userId;

    private String title;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String repeatDay; // 예: "MON,WED"
}