package org.jume.loyalitybot.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "broadcast_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BroadcastJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "image_file_id")
    private String imageFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BroadcastStatus status = BroadcastStatus.PENDING;

    @Column(name = "total_recipients", nullable = false)
    @Builder.Default
    private Integer totalRecipients = 0;

    @Column(name = "sent_count", nullable = false)
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum BroadcastStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    public void start(int totalRecipients) {
        this.status = BroadcastStatus.IN_PROGRESS;
        this.totalRecipients = totalRecipients;
        this.startedAt = LocalDateTime.now();
    }

    public void incrementSentCount() {
        this.sentCount++;
    }

    public void incrementFailedCount() {
        this.failedCount++;
    }

    public void complete() {
        this.status = BroadcastStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isComplete() {
        return sentCount + failedCount >= totalRecipients;
    }
}
