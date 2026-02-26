package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.BonusNotification;
import org.jume.loyalitybot.model.BonusNotification.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BonusNotificationRepository extends JpaRepository<BonusNotification, Long> {

    List<BonusNotification> findByStatus(NotificationStatus status);

    @Query("SELECT bn FROM BonusNotification bn WHERE bn.status = :status " +
           "AND bn.nextRetryAt IS NOT NULL AND bn.nextRetryAt <= :now " +
           "AND bn.retryCount < :maxRetries ORDER BY bn.nextRetryAt ASC")
    List<BonusNotification> findPendingRetries(
            @Param("status") NotificationStatus status,
            @Param("now") LocalDateTime now,
            @Param("maxRetries") int maxRetries);

    @Query("SELECT bn FROM BonusNotification bn WHERE bn.status = 'PENDING' " +
           "ORDER BY bn.createdAt ASC")
    List<BonusNotification> findPendingNotifications();

    List<BonusNotification> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    long countByStatus(NotificationStatus status);
}
