package org.jume.loyalitybot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.PosterWebhookPayload;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.service.CustomerService;
import org.jume.loyalitybot.service.NotificationRetryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class PosterWebhookController {

    private final CustomerService customerService;
    private final NotificationRetryService notificationRetryService;

    @Value("${poster.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/poster")
    public ResponseEntity<String> onPosterWebhook(@RequestBody String rawPayload) {
        log.info("Received Poster webhook raw: {}", rawPayload);

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            PosterWebhookPayload payload = mapper.readValue(rawPayload, PosterWebhookPayload.class);

            log.info("Parsed webhook: object={}, action={}, objectId={}, data={}",
                    payload.getObjectType(), payload.getAction(), payload.getObjectId(), payload.getData());

            processWebhook(payload);
        } catch (Exception e) {
            log.error("Error processing Poster webhook: {}", e.getMessage());
        }

        return ResponseEntity.ok("OK");
    }

    private void processWebhook(PosterWebhookPayload payload) {
        if (!"transaction".equals(payload.getObjectType())) {
            log.debug("Ignoring non-transaction webhook: {}", payload.getObjectType());
            return;
        }

        if (!"added".equals(payload.getAction()) && !"changed".equals(payload.getAction())) {
            log.debug("Ignoring webhook action: {}", payload.getAction());
            return;
        }

        PosterWebhookPayload.TransactionData data = payload.getData();
        if (data == null || data.getClientId() == null) {
            log.debug("Webhook has no client data, skipping");
            return;
        }

        Optional<Customer> customerOpt = customerService.findByPosterClientId(data.getClientId());
        if (customerOpt.isEmpty()) {
            log.debug("No registered customer for Poster client {}", data.getClientId());
            return;
        }

        Customer customer = customerOpt.get();

        // Handle bonus earned
        BigDecimal bonusAdded = data.getBonusAdded();
        if (bonusAdded != null && bonusAdded.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Creating bonus earned notification for customer {} (Poster: {}), bonus: +{}",
                    customer.getTelegramId(), data.getClientId(), bonusAdded);
            notificationRetryService.createNotification(customer, bonusAdded, data.getTransactionId());
        }

        // Handle bonus spent
        BigDecimal bonusUsed = data.getBonusUsed();
        if (bonusUsed != null && bonusUsed.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Creating bonus spent notification for customer {} (Poster: {}), bonus: -{}",
                    customer.getTelegramId(), data.getClientId(), bonusUsed);
            // Use negative value to indicate spent
            notificationRetryService.createNotification(customer, bonusUsed.negate(), data.getTransactionId());
        }
    }
}
