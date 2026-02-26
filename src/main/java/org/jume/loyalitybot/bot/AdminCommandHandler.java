package org.jume.loyalitybot.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.model.BroadcastJob;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.service.BroadcastService;
import org.jume.loyalitybot.service.CustomerService;
import org.jume.loyalitybot.service.NotificationRetryService;
import org.jume.loyalitybot.service.TelegramBotService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminCommandHandler {

    private final TelegramBotService telegramBotService;
    private final CustomerService customerService;
    private final BroadcastService broadcastService;
    private final NotificationRetryService notificationRetryService;

    public void handleBroadcast(Long chatId, Long adminId, String message) {
        log.info("Admin {} initiating broadcast", adminId);

        if (message == null || message.isBlank()) {
            telegramBotService.sendMessage(chatId,
                    "Використання: /broadcast <повідомлення>\n\n" +
                    "Приклад: /broadcast Завтра знижка 20% на всю каву!");
            return;
        }

        long activeCustomers = customerService.getActiveCustomersCount();

        if (activeCustomers == 0) {
            telegramBotService.sendMessage(chatId, "Немає активних користувачів для розсилки.");
            return;
        }

        BroadcastJob job = broadcastService.createBroadcast(message, adminId);

        String confirmation = String.format(
                "<b>Розсилка створена</b>\n\n" +
                "ID: %d\n" +
                "Отримувачів: %d\n" +
                "Статус: %s\n\n" +
                "Повідомлення:\n%s",
                job.getId(),
                job.getTotalRecipients(),
                job.getStatus(),
                message
        );

        telegramBotService.sendMessage(chatId, confirmation);
    }

    public void handleStats(Long chatId) {
        log.debug("Generating stats for admin");

        long totalCustomers = customerService.getTotalCustomersCount();
        long activeCustomers = customerService.getActiveCustomersCount();
        long pendingNotifications = notificationRetryService.getPendingNotificationsCount();
        long failedNotifications = notificationRetryService.getFailedNotificationsCount();

        String stats = String.format("""
                <b>Статистика</b>

                <b>Користувачі:</b>
                Всього: %d
                Активних: %d
                Очікують реєстрації: %d

                <b>Сповіщення:</b>
                В очікуванні: %d
                Невдалих: %d""",
                totalCustomers,
                activeCustomers,
                totalCustomers - activeCustomers,
                pendingNotifications,
                failedNotifications
        );

        telegramBotService.sendMessage(chatId, stats);
    }

    public void handleSend(Long chatId, String args) {
        if (args == null || args.isBlank()) {
            telegramBotService.sendMessage(chatId,
                    "Використання: /send <telegram_id> <повідомлення>\n\n" +
                    "Приклад: /send 123456789 Вітаємо з днем народження!");
            return;
        }

        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            telegramBotService.sendMessage(chatId,
                    "Невірний формат. Використання: /send <telegram_id> <повідомлення>");
            return;
        }

        try {
            Long targetTelegramId = Long.parseLong(parts[0]);
            String message = parts[1];

            Optional<Customer> customer = customerService.findByTelegramId(targetTelegramId);

            if (customer.isEmpty()) {
                telegramBotService.sendMessage(chatId,
                        "Користувача з ID " + targetTelegramId + " не знайдено.");
                return;
            }

            telegramBotService.sendMessage(targetTelegramId, message);
            telegramBotService.sendMessage(chatId,
                    "Повідомлення надіслано користувачу " + customer.get().getDisplayName());

        } catch (NumberFormatException e) {
            telegramBotService.sendMessage(chatId, "Невірний формат telegram_id. Має бути числом.");
        } catch (Exception e) {
            log.error("Error sending message", e);
            telegramBotService.sendMessage(chatId, "Помилка надсилання повідомлення: " + e.getMessage());
        }
    }
}
