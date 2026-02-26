package org.jume.loyalitybot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.bot.LoyaltyBot;
import org.jume.loyalitybot.dto.TelegramUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final LoyaltyBot loyaltyBot;

    @PostMapping("/telegram")
    public ResponseEntity<Void> onUpdateReceived(@RequestBody TelegramUpdate update) {
        log.debug("Received Telegram webhook update: {}", update.getUpdateId());

        try {
            loyaltyBot.processUpdate(update);
        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
        }

        return ResponseEntity.ok().build();
    }
}
