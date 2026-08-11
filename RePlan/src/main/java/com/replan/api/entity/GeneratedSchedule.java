package com.replan.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scheduleId;

    @Column(nullable = false)
    private Long userId;

    private Long taskId;

    private String title;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Builder.Default
    private Boolean isPinned = false;

    private String blockId;

    private Integer stepOrder;

    private String source;

    @Builder.Default
    private Boolean locked = false;

    @Builder.Default
    private Boolean completed = false;

    private String reasonCode;

    private String reason;

    public void updateStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void updateEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void updateLocked(boolean locked) {
        this.locked = locked;
    }

    public void updateCompleted(boolean completed) {
        this.completed = completed;
    }
}