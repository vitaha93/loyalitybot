package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.BroadcastJob;
import org.jume.loyalitybot.model.BroadcastJob.BroadcastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BroadcastJobRepository extends JpaRepository<BroadcastJob, Long> {

    List<BroadcastJob> findByStatus(BroadcastStatus status);

    List<BroadcastJob> findByStatusIn(List<BroadcastStatus> statuses);

    Optional<BroadcastJob> findFirstByStatusOrderByCreatedAtAsc(BroadcastStatus status);

    List<BroadcastJob> findByCreatedByOrderByCreatedAtDesc(Long createdBy);

    long countByStatus(BroadcastStatus status);
}
