package org.jume.loyalitybot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_type", nullable = false, unique = true)
    private String syncType;

    @Column(name = "last_sync_date")
    private LocalDate lastSyncDate;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "records_synced")
    private Integer recordsSynced;

    public static final String TRANSACTIONS = "TRANSACTIONS";
}
