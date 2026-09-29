package org.jume.loyalitybot.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "birthday_greetings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BirthdayGreeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "poster_client_id")
    private Long posterClientId;

    /**
     * Year the greeting was sent for. Together with the customer it forms a unique key,
     * so a customer can only be greeted once per year even if the job runs twice.
     */
    @Column(name = "greeting_year", nullable = false)
    private Integer greetingYear;

    @Column(name = "bonus_amount", precision = 10, scale = 2)
    private BigDecimal bonusAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private GreetingStatus status = GreetingStatus.PENDING;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public enum GreetingStatus {
        PENDING,
        SENT,
        SENT_WITHOUT_BONUS,
        FAILED
    }

    public void markAsSent(boolean bonusGranted) {
        this.status = bonusGranted ? GreetingStatus.SENT : GreetingStatus.SENT_WITHOUT_BONUS;
        this.sentAt = LocalDateTime.now();
    }

    public void markAsFailed(String error) {
        this.status = GreetingStatus.FAILED;
        this.lastError = error;
    }
}
