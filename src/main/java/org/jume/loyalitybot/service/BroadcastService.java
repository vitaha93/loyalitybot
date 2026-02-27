package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.model.BroadcastJob;
import org.jume.loyalitybot.model.BroadcastJob.BroadcastStatus;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.repository.BroadcastJobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final BroadcastJobRepository broadcastJobRepository;
    private final CustomerService customerService;
    private final TelegramBotService telegramBotService;

    @Transactional
    public BroadcastJob createBroadcast(String message, Long createdBy) {
        return createBroadcast(message, null, createdBy);
    }

    @Transactional
    public BroadcastJob createBroadcast(String message, String imageFileId, Long createdBy) {
        List<Customer> recipients = customerService.getActiveCustomers();

        BroadcastJob job = BroadcastJob.builder()
                .message(message)
                .imageFileId(imageFileId)
                .createdBy(createdBy)
                .totalRecipients(recipients.size())
                .build();

        job = broadcastJobRepository.save(job);
        log.info("Created broadcast job {} for {} recipients", job.getId(), recipients.size());
        return job;
    }

    @Scheduled(cron = "${scheduling.broadcast-cron:0 */1 * * * *}")
    @Transactional
    public void processPendingBroadcasts() {
        broadcastJobRepository.findFirstByStatusOrderByCreatedAtAsc(BroadcastStatus.PENDING)
                .ifPresent(this::startBroadcast);

        broadcastJobRepository.findByStatus(BroadcastStatus.IN_PROGRESS)
                .forEach(this::continueBroadcast);
    }

    @Transactional
    public void startBroadcast(BroadcastJob job) {
        List<Customer> recipients = customerService.getActiveCustomers();
        job.start(recipients.size());
        broadcastJobRepository.save(job);

        log.info("Starting broadcast job {} to {} recipients", job.getId(), recipients.size());
        sendToRecipients(job, recipients, 0);
    }

    private void continueBroadcast(BroadcastJob job) {
        if (job.isComplete()) {
            job.complete();
            broadcastJobRepository.save(job);
            log.info("Completed broadcast job {}: sent={}, failed={}",
                    job.getId(), job.getSentCount(), job.getFailedCount());
            return;
        }

        List<Customer> recipients = customerService.getActiveCustomers();
        int startFrom = job.getSentCount() + job.getFailedCount();
        sendToRecipients(job, recipients, startFrom);
    }

    private void sendToRecipients(BroadcastJob job, List<Customer> recipients, int startFrom) {
        int batchSize = 30;
        int endIndex = Math.min(startFrom + batchSize, recipients.size());

        for (int i = startFrom; i < endIndex; i++) {
            Customer customer = recipients.get(i);
            try {
                telegramBotService.sendMessage(customer.getTelegramId(), job.getMessage());
                job.incrementSentCount();
                Thread.sleep(50);
            } catch (Exception e) {
                log.warn("Failed to send broadcast to customer {}: {}", customer.getTelegramId(), e.getMessage());
                job.incrementFailedCount();
            }
        }

        if (job.isComplete()) {
            job.complete();
        }

        broadcastJobRepository.save(job);
    }

    public BroadcastJob getBroadcastStatus(Long jobId) {
        return broadcastJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Broadcast job not found: " + jobId));
    }

    public List<BroadcastJob> getRecentBroadcasts(Long createdBy) {
        return broadcastJobRepository.findByCreatedByOrderByCreatedAtDesc(createdBy);
    }

    public List<BroadcastJob> getAllBroadcasts() {
        return broadcastJobRepository.findAllByOrderByCreatedAtDesc();
    }
}
