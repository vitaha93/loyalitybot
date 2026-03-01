package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.model.BonusNotification;
import org.jume.loyalitybot.model.BonusNotification.NotificationStatus;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.repository.BonusNotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryService {

    private final BonusNotificationRepository notificationRepository;
    private final TelegramBotService telegramBotService;
    private final PosterApiService posterApiService;

    @Value("${notification.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${notification.retry.initial-delay-ms:60000}")
    private long initialDelayMs;

    @Value("${notification.retry.multiplier:2}")
    private int multiplier;

    @Transactional
    public BonusNotification createNotification(Customer customer, BigDecimal bonusAmount, String transactionId) {
        return createNotification(customer, bonusAmount, null, transactionId);
    }

    @Transactional
    public BonusNotification createNotification(Customer customer, BigDecimal bonusAmount, BigDecimal totalBonus, String transactionId) {
        BonusNotification notification = BonusNotification.builder()
                .customer(customer)
                .bonusAmount(bonusAmount)
                .transactionId(transactionId)
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);
        log.info("Created bonus notification {} for customer {}, amount: {}, total: {}",
                notification.getId(), customer.getTelegramId(), bonusAmount, totalBonus);

        sendNotificationWithTotal(notification, totalBonus);
        return notification;
    }

    @Scheduled(cron = "${scheduling.retry-cron:0 */5 * * * *}")
    @Transactional
    public void processRetries() {
        List<BonusNotification> pendingRetries = notificationRepository.findPendingRetries(
                NotificationStatus.PENDING,
                LocalDateTime.now(),
                maxRetryAttempts
        );

        log.debug("Processing {} pending notification retries", pendingRetries.size());

        for (BonusNotification notification : pendingRetries) {
            sendNotification(notification);
        }
    }

    @Transactional
    public void sendNotification(BonusNotification notification) {
        sendNotificationWithTotal(notification, null);
    }

    @Transactional
    public void sendNotificationWithTotal(BonusNotification notification, BigDecimal providedTotalBonus) {
        Customer customer = notification.getCustomer();

        try {
            BigDecimal totalBonus = providedTotalBonus;
            if (totalBonus == null) {
                totalBonus = posterApiService.getClientBonus(customer.getPosterClientId())
                        .orElse(notification.getBonusAmount());
            }

            telegramBotService.sendBonusNotification(
                    customer.getTelegramId(),
                    customer.getDisplayName(),
                    notification.getBonusAmount(),
                    totalBonus
            );

            notification.markAsSent();
            notificationRepository.save(notification);
            log.info("Successfully sent notification {} to customer {}", notification.getId(), customer.getTelegramId());

        } catch (Exception e) {
            log.warn("Failed to send notification {} to customer {}: {}",
                    notification.getId(), customer.getTelegramId(), e.getMessage());

            notification.markAsFailed(e.getMessage());

            if (notification.getRetryCount() < maxRetryAttempts) {
                long delayMs = calculateNextRetryDelay(notification.getRetryCount());
                notification.setNextRetryAt(LocalDateTime.now().plusNanos(delayMs * 1_000_000));
            } else {
                notification.setStatus(NotificationStatus.FAILED);
                log.error("Notification {} permanently failed after {} attempts",
                        notification.getId(), notification.getRetryCount());
            }

            notificationRepository.save(notification);
        }
    }

    private long calculateNextRetryDelay(int retryCount) {
        return initialDelayMs * (long) Math.pow(multiplier, retryCount);
    }

    public long getPendingNotificationsCount() {
        return notificationRepository.countByStatus(NotificationStatus.PENDING);
    }

    public long getFailedNotificationsCount() {
        return notificationRepository.countByStatus(NotificationStatus.FAILED);
    }
}
