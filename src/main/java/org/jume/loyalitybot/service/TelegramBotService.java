package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.LoyaltyConfig;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotService {

    private final TelegramApiClient telegramApiClient;
    private final LoyaltyConfig loyaltyConfig;

    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, false);
    }

    public void sendMessage(Long chatId, String text, boolean removeKeyboard) {
        Object replyMarkup = removeKeyboard ? telegramApiClient.removeKeyboard() : null;
        telegramApiClient.sendMessage(chatId, text, "HTML", replyMarkup);
        log.debug("Message sent to chat {}", chatId);
    }

    public void sendMessageWithMainMenu(Long chatId, String text) {
        telegramApiClient.sendMessage(chatId, text, "HTML", telegramApiClient.createMainMenuKeyboard());
        log.debug("Message with main menu sent to chat {}", chatId);
    }

    public void sendPhoto(Long chatId, byte[] imageData, String caption) {
        telegramApiClient.sendPhoto(chatId, imageData, caption, "HTML");
        log.debug("Photo sent to chat {}", chatId);
    }

    public void sendBonusNotification(Long chatId, String customerName, BigDecimal bonusAmount, BigDecimal totalBonus) {
        String message;
        if (bonusAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Bonus earned
            message = String.format(
                    "<b>Дякуємо за покупку!</b>\n\n" +
                    "Нараховано бонусів: <b>+%s %s</b>\n" +
                    "Ваш баланс: <b>%s %s</b>",
                    bonusAmount.stripTrailingZeros().toPlainString(),
                    loyaltyConfig.getCurrencySymbol(),
                    totalBonus.stripTrailingZeros().toPlainString(),
                    loyaltyConfig.getCurrencySymbol()
            );
        } else {
            // Bonus spent
            message = String.format(
                    "<b>Бонуси списано!</b>\n\n" +
                    "Використано: <b>%s %s</b>\n" +
                    "Залишок: <b>%s %s</b>",
                    bonusAmount.abs().stripTrailingZeros().toPlainString(),
                    loyaltyConfig.getCurrencySymbol(),
                    totalBonus.stripTrailingZeros().toPlainString(),
                    loyaltyConfig.getCurrencySymbol()
            );
        }
        sendMessage(chatId, message);
    }

    public void requestContact(Long chatId) {
        telegramApiClient.sendMessage(
                chatId,
                "Для реєстрації в програмі лояльності, будь ласка, поділіться своїм номером телефону:",
                null,
                telegramApiClient.createContactKeyboard()
        );
        log.debug("Contact request sent to chat {}", chatId);
    }

    public void sendWelcomeMessage(Long chatId, String customerName, BigDecimal welcomeBonus) {
        String message = String.format(
                "<b>Вітаємо в програмі лояльності!</b>\n\n" +
                "%s, ви успішно зареєстровані!\n\n" +
                "Вам нараховано вітальний бонус: <b>%s %s</b>\n\n" +
                "Використовуйте кнопки меню нижче 👇",
                customerName,
                welcomeBonus.stripTrailingZeros().toPlainString(),
                loyaltyConfig.getCurrencySymbol()
        );
        sendMessageWithMainMenu(chatId, message);
    }
}
