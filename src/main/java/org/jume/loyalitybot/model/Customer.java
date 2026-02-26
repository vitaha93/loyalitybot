package org.jume.loyalitybot.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "poster_client_id", unique = true)
    private Long posterClientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.PENDING_PHONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum CustomerStatus {
        PENDING_PHONE,
        ACTIVE,
        BLOCKED
    }

    public String getDisplayName() {
        if (firstName != null && !firstName.isBlank()) {
            return lastName != null && !lastName.isBlank()
                ? firstName + " " + lastName
                : firstName;
        }
        return telegramUsername != null ? "@" + telegramUsername : "Користувач";
    }
}
