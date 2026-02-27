package org.jume.loyalitybot.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.AdminConfig;
import org.jume.loyalitybot.dto.TelegramUpdate;
import org.jume.loyalitybot.dto.TelegramUpdate.TelegramContact;
import org.jume.loyalitybot.dto.TelegramUpdate.TelegramMessage;
import org.jume.loyalitybot.dto.TelegramUpdate.TelegramUser;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.model.Customer.CustomerStatus;
import org.jume.loyalitybot.service.CustomerService;
import org.jume.loyalitybot.service.TelegramBotService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommandHandler {

    private final UserCommandHandler userCommandHandler;
    private final AdminCommandHandler adminCommandHandler;
    private final CustomerService customerService;
    private final TelegramBotService telegramBotService;
    private final AdminConfig adminConfig;

    public void handleUpdate(TelegramUpdate update) {
        if (!update.hasMessage()) {
            return;
        }

        TelegramMessage message = update.getMessage();
        TelegramUser telegramUser = message.getFrom();
        Long chatId = message.getChat().getId();

        log.debug("Received message from user {} ({})", telegramUser.getId(), telegramUser.getUsername());

        if (update.hasContact()) {
            handleContact(telegramUser, message.getContact());
            return;
        }

        if (!update.hasText()) {
            return;
        }

        String text = message.getText().trim();

        if (text.startsWith("/")) {
            handleCommand(telegramUser, chatId, text);
        } else {
            handleButtonText(telegramUser, chatId, text);
        }
    }

    private void handleButtonText(TelegramUser telegramUser, Long chatId, String text) {
        Customer customer = customerService.getOrCreateCustomer(telegramUser);

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            telegramBotService.sendMessage(chatId,
                    "Будь ласка, завершіть реєстрацію, поділившись номером телефону.");
            telegramBotService.requestContact(chatId);
            return;
        }

        switch (text) {
            case "💰 Баланс" -> userCommandHandler.handleBalance(chatId, customer);
            case "🎫 Картка" -> userCommandHandler.handleCard(chatId, customer);
            case "❓ Допомога" -> userCommandHandler.handleHelp(chatId);
        }
    }

    private void handleCommand(TelegramUser telegramUser, Long chatId, String text) {
        String command = text.split("\\s+")[0].toLowerCase();
        String args = text.length() > command.length() ? text.substring(command.length()).trim() : "";

        Customer customer = customerService.getOrCreateCustomer(telegramUser);

        if (command.equals("/start")) {
            handleStart(customer, chatId);
            return;
        }

        if (adminConfig.isAdmin(telegramUser.getId())) {
            if (tryAdminCommand(command, args, chatId, telegramUser.getId())) {
                return;
            }
        }

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            telegramBotService.sendMessage(chatId,
                    "Будь ласка, завершіть реєстрацію, поділившись номером телефону.");
            telegramBotService.requestContact(chatId);
            return;
        }

        handleUserCommand(command, args, chatId, customer);
    }

    private void handleStart(Customer customer, Long chatId) {
        if (customer.getStatus() == CustomerStatus.ACTIVE) {
            String message = String.format(
                    "Вітаю, %s!\n\n" +
                    "Ви вже зареєстровані в програмі лояльності.\n\n" +
                    "Використовуйте кнопки меню нижче 👇",
                    customer.getDisplayName()
            );
            telegramBotService.sendMessageWithMainMenu(chatId, message);
        } else {
            String message = "Вітаю в програмі лояльності нашої кав'ярні!\n\n" +
                    "Для реєстрації, будь ласка, поділіться своїм номером телефону.";
            telegramBotService.sendMessage(chatId, message);
            telegramBotService.requestContact(chatId);
        }
    }

    private void handleContact(TelegramUser telegramUser, TelegramContact contact) {
        Optional<Customer> customerOpt = customerService.findByTelegramId(telegramUser.getId());

        if (customerOpt.isEmpty()) {
            log.warn("Received contact from unknown user: {}", telegramUser.getId());
            return;
        }

        Customer customer = customerOpt.get();

        if (customer.getStatus() == CustomerStatus.ACTIVE) {
            telegramBotService.sendMessage(telegramUser.getId(),
                    "Ви вже зареєстровані в програмі лояльності!");
            return;
        }

        customerService.processPhoneRegistration(telegramUser.getId(), contact);
    }

    private boolean tryAdminCommand(String command, String args, Long chatId, Long adminId) {
        return switch (command) {
            case "/broadcast" -> {
                adminCommandHandler.handleBroadcast(chatId, adminId, args);
                yield true;
            }
            case "/stats" -> {
                adminCommandHandler.handleStats(chatId);
                yield true;
            }
            case "/send" -> {
                adminCommandHandler.handleSend(chatId, args);
                yield true;
            }
            default -> false;
        };
    }

    private void handleUserCommand(String command, String args, Long chatId, Customer customer) {
        switch (command) {
            case "/balance" -> userCommandHandler.handleBalance(chatId, customer);
            case "/card" -> userCommandHandler.handleCard(chatId, customer);
            case "/help" -> userCommandHandler.handleHelp(chatId);
            default -> telegramBotService.sendMessage(chatId,
                    "Невідома команда. Введіть /help для списку доступних команд.");
        }
    }
}
