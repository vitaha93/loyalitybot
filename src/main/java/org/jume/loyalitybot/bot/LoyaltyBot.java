package org.jume.loyalitybot.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.TelegramUpdate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyBot {

    private final CommandHandler commandHandler;

    public void processUpdate(TelegramUpdate update) {
        try {
            log.debug("Processing update: {}", update.getUpdateId());
            commandHandler.handleUpdate(update);
        } catch (Exception e) {
            log.error("Error processing update: {}", update.getUpdateId(), e);
        }
    }
}
