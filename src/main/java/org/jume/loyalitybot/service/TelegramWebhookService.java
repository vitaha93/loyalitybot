package org.jume.loyalitybot.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.TelegramBotConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookService {

    private final TelegramBotConfig config;

    @Value("${RAILWAY_PUBLIC_DOMAIN:}")
    private String railwayDomain;

    @PostConstruct
    public void registerWebhook() {
        if (railwayDomain == null || railwayDomain.isBlank()) {
            log.warn("RAILWAY_PUBLIC_DOMAIN not set, skipping webhook registration");
            return;
        }

        String webhookUrl = "https://" + railwayDomain + config.getWebhookPath();

        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + config.getToken())
                .build();

        try {
            String response = restClient.post()
                    .uri("/setWebhook?url={url}", webhookUrl)
                    .retrieve()
                    .body(String.class);
            log.info("Webhook registered: {} - Response: {}", webhookUrl, response);
        } catch (Exception e) {
            log.error("Failed to register webhook: {}", e.getMessage());
        }
    }
}
