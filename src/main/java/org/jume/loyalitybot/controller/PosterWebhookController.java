package org.jume.loyalitybot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ObjectMapper objectMapper;

    @Value("${poster.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/poster")
    public ResponseEntity<String> onPosterWebhook(@RequestBody String rawPayload) {
        log.info("Received Poster webhook raw: {}", rawPayload);

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String objectType = root.path("object").asText();
            String action = root.path("action").asText();
            String objectId = root.path("object_id").asText();

            log.info("Webhook: object={}, action={}, objectId={}", objectType, action, objectId);

            // Handle client_bonus webhook - this is what Poster sends when bonuses change
            if ("client_bonus".equals(objectType) && "changed".equals(action)) {
                processClientBonusWebhook(objectId, root.path("data"));
            }

        } catch (Exception e) {
            log.error("Error processing Poster webhook: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok("OK");
    }

    private void processClientBonusWebhook(String clientIdStr, JsonNode dataNode) {
        try {
            Long clientId = Long.parseLong(clientIdStr);

            // Parse data - it can be a string or object
            JsonNode bonusData = dataNode;
            if (dataNode.isTextual()) {
                bonusData = objectMapper.readTree(dataNode.asText());
            }

            // value_relative = bonus change (positive = earned, negative = spent)
            // value_absolute = total bonus after change
            BigDecimal valueRelative = BigDecimal.ZERO;
            BigDecimal valueAbsolute = BigDecimal.ZERO;

            if (bonusData.has("value_relative")) {
                valueRelative = new BigDecimal(bonusData.get("value_relative").asText());
            }
            if (bonusData.has("value_absolute")) {
                valueAbsolute = new BigDecimal(bonusData.get("value_absolute").asText());
            }

            log.info("Client bonus webhook: clientId={}, change={}, total={}",
                    clientId, valueRelative, valueAbsolute);

            // Skip if no significant change (avoid floating point noise)
            if (valueRelative.abs().compareTo(new BigDecimal("0.01")) < 0) {
                log.debug("Ignoring tiny bonus change: {}", valueRelative);
                return;
            }

            Optional<Customer> customerOpt = customerService.findByPosterClientId(clientId);
            if (customerOpt.isEmpty()) {
                log.debug("No registered customer for Poster client {}", clientId);
                return;
            }

            Customer customer = customerOpt.get();

            log.info("Creating bonus notification for customer {} (Poster: {}), change: {}, total: {}",
                    customer.getTelegramId(), clientId, valueRelative, valueAbsolute);

            notificationRetryService.createNotification(
                    customer,
                    valueRelative,
                    valueAbsolute,
                    "webhook-" + System.currentTimeMillis()
            );

        } catch (Exception e) {
            log.error("Error processing client_bonus webhook: {}", e.getMessage(), e);
        }
    }
}
