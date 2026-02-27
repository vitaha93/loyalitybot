package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.admin.TransactionDto;
import org.jume.loyalitybot.model.PosterTransaction;
import org.jume.loyalitybot.model.PosterTransactionProduct;
import org.jume.loyalitybot.model.SyncStatus;
import org.jume.loyalitybot.repository.PosterTransactionRepository;
import org.jume.loyalitybot.repository.SyncStatusRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionSyncService {

    private final PosterApiService posterApiService;
    private final PosterTransactionRepository transactionRepository;
    private final SyncStatusRepository syncStatusRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Sync transactions every 15 minutes
     */
    @Scheduled(fixedRate = 15 * 60 * 1000, initialDelay = 30 * 1000)
    public void scheduledSync() {
        log.info("Starting scheduled transaction sync");
        syncTransactions();
    }

    /**
     * Manual sync trigger
     */
    @Transactional
    public int syncTransactions() {
        SyncStatus syncStatus = syncStatusRepository.findBySyncType(SyncStatus.TRANSACTIONS)
                .orElse(SyncStatus.builder()
                        .syncType(SyncStatus.TRANSACTIONS)
                        .build());

        LocalDate today = LocalDate.now();
        LocalDate syncFromDate;

        if (syncStatus.getLastSyncDate() == null) {
            // First sync - get ALL historical data (from 2020)
            syncFromDate = LocalDate.of(2020, 1, 1);
            log.info("First sync - fetching ALL transactions from {}", syncFromDate);
        } else {
            // Incremental sync - from last sync date
            syncFromDate = syncStatus.getLastSyncDate();
            log.info("Incremental sync - fetching transactions from {}", syncFromDate);
        }

        int totalSynced = 0;

        try {
            // Fetch transactions in monthly chunks (Poster API has date range limits)
            LocalDate chunkStart = syncFromDate;
            while (chunkStart.isBefore(today) || chunkStart.isEqual(today)) {
                LocalDate chunkEnd = chunkStart.plusMonths(1).minusDays(1);
                if (chunkEnd.isAfter(today)) {
                    chunkEnd = today;
                }

                log.info("Fetching transactions for period {} to {}", chunkStart, chunkEnd);
                List<TransactionDto> transactions = posterApiService.getAllTransactions(chunkStart, chunkEnd);
                log.info("Fetched {} transactions from Poster for period {} to {}", transactions.size(), chunkStart, chunkEnd);

                totalSynced += processTransactions(transactions);

                chunkStart = chunkEnd.plusDays(1);
            }

            // Update sync status
            syncStatus.setLastSyncDate(today);
            syncStatus.setLastSyncAt(LocalDateTime.now());
            syncStatus.setRecordsSynced(totalSynced);
            syncStatusRepository.save(syncStatus);

            log.info("Transaction sync completed. Synced {} new transactions", totalSynced);

        } catch (Exception e) {
            log.error("Error during transaction sync", e);
        }

        return totalSynced;
    }

    private int processTransactions(List<TransactionDto> transactions) {
        int synced = 0;
        for (TransactionDto dto : transactions) {
            if (dto.getTransactionId() == null) continue;

            // Skip if already exists
            if (transactionRepository.existsByPosterTransactionId(dto.getTransactionId())) {
                continue;
            }

            try {
                // Create transaction entity
                PosterTransaction transaction = PosterTransaction.builder()
                        .posterTransactionId(dto.getTransactionId())
                        .posterClientId(dto.getClientId())
                        .clientFirstName(dto.getClientFirstName())
                        .clientLastName(dto.getClientLastName())
                        .dateClose(dto.getDateClose())
                        .transactionDate(parseDateTime(dto.getDateClose()))
                        .sum(dto.getSum())
                        .payedSum(dto.getPayedSum())
                        .payedBonus(dto.getPayedBonus())
                        .bonusEarned(dto.getBonusEarned())
                        .discount(dto.getDiscount())
                        .build();

                // Fetch products for this transaction
                List<TransactionDto.TransactionProduct> products =
                        posterApiService.getTransactionProducts(dto.getTransactionId());

                for (TransactionDto.TransactionProduct productDto : products) {
                    PosterTransactionProduct product = PosterTransactionProduct.builder()
                            .posterClientId(dto.getClientId())
                            .posterProductId(productDto.getProductId())
                            .productName(productDto.getProductName())
                            .count(productDto.getCount() != null ? productDto.getCount() : 1)
                            .price(productDto.getPrice())
                            .build();
                    transaction.addProduct(product);
                }

                transactionRepository.save(transaction);
                synced++;

                // Log progress every 100 transactions
                if (synced % 100 == 0) {
                    log.info("Synced {} transactions...", synced);
                }
            } catch (Exception e) {
                log.warn("Failed to process transaction {}: {}", dto.getTransactionId(), e.getMessage());
            }
        }
        return synced;
    }

    private LocalDateTime parseDateTime(String dateClose) {
        if (dateClose == null || dateClose.isBlank()) {
            return null;
        }
        try {
            // Try parsing as formatted date first
            return LocalDateTime.parse(dateClose, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            try {
                // Try parsing as Unix timestamp (milliseconds)
                long timestamp = Long.parseLong(dateClose);
                return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp),
                    java.time.ZoneId.systemDefault()
                );
            } catch (Exception e2) {
                log.warn("Failed to parse date: {}", dateClose);
                return null;
            }
        }
    }
}
