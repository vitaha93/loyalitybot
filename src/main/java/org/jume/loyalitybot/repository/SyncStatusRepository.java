package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyncStatusRepository extends JpaRepository<SyncStatus, Long> {

    Optional<SyncStatus> findBySyncType(String syncType);
}
