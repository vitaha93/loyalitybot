package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.AdminConfig;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class BirthdayNotificationService {

    private final PosterApiService posterApiService;
    private final TelegramBotService telegramBotService;
    private final AdminConfig adminConfig;

    private static final List<DateTimeFormatter> BIRTHDAY_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd")  // Some systems store just month-day
    );

    /**
     * Runs every day at 8:00 AM Kyiv time.
     * Checks all Poster clients for birthdays and notifies admins.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Kyiv")
    public void checkBirthdays() {
        log.info("Starting birthday check");

        Set<Long> adminIds = adminConfig.getAdminTelegramIds();
        if (adminIds.isEmpty()) {
            log.warn("No admin telegram IDs configured, skipping birthday notifications");
            return;
        }

        LocalDate today = LocalDate.now();
        int todayMonth = today.getMonthValue();
        int todayDay = today.getDayOfMonth();

        List<PosterClientDto> allClients = posterApiService.getAllClients();
        List<PosterClientDto> birthdayClients = new ArrayList<>();

        for (PosterClientDto client : allClients) {
            if (hasBirthdayToday(client.getBirthday(), todayMonth, todayDay)) {
                birthdayClients.add(client);
            }
        }

        if (birthdayClients.isEmpty()) {
            log.info("No birthdays today");
            return;
        }

        log.info("Found {} clients with birthdays today", birthdayClients.size());

        String message = formatBirthdayMessage(birthdayClients);

        for (Long adminId : adminIds) {
            try {
                telegramBotService.sendMessage(adminId, message);
                log.info("Sent birthday notification to admin {}", adminId);
            } catch (Exception e) {
                log.error("Failed to send birthday notification to admin {}: {}", adminId, e.getMessage());
            }
        }
    }

    private boolean hasBirthdayToday(String birthday, int todayMonth, int todayDay) {
        if (birthday == null || birthday.isBlank()) {
            return false;
        }

        // Try to parse birthday in various formats
        for (DateTimeFormatter formatter : BIRTHDAY_FORMATTERS) {
            try {
                // For month-day only format
                if (formatter.toString().contains("MM-dd") && birthday.matches("\\d{2}-\\d{2}")) {
                    String[] parts = birthday.split("-");
                    int month = Integer.parseInt(parts[0]);
                    int day = Integer.parseInt(parts[1]);
                    return month == todayMonth && day == todayDay;
                }

                LocalDate birthDate = LocalDate.parse(birthday, formatter);
                return birthDate.getMonthValue() == todayMonth && birthDate.getDayOfMonth() == todayDay;
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        // Try to extract month and day from any format with numbers
        try {
            // Pattern: could be "dd.MM" or "MM.dd" or similar
            String[] parts = birthday.split("[.\\-/]");
            if (parts.length >= 2) {
                int first = Integer.parseInt(parts[0].trim());
                int second = Integer.parseInt(parts[1].trim());

                // Assume dd.MM format (European)
                if (first <= 31 && second <= 12) {
                    return first == todayDay && second == todayMonth;
                }
                // Try MM.dd format (American)
                if (first <= 12 && second <= 31) {
                    return first == todayMonth && second == todayDay;
                }
            }
        } catch (Exception ignored) {
        }

        log.debug("Could not parse birthday: {}", birthday);
        return false;
    }

    private String formatBirthdayMessage(List<PosterClientDto> clients) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>🎂 Дні народження сьогодні:</b>\n\n");

        for (PosterClientDto client : clients) {
            String name = formatClientName(client);
            sb.append("• <b>").append(name).append("</b>");

            if (client.getNormalizedPhone() != null) {
                sb.append(" (").append(client.getNormalizedPhone()).append(")");
            }

            sb.append("\n");

            if (client.getBonusInHryvnia() != null && client.getBonusInHryvnia().compareTo(java.math.BigDecimal.ZERO) > 0) {
                sb.append("  Бонуси: ").append(client.getBonusInHryvnia()).append(" грн\n");
            }
        }

        sb.append("\n<i>Не забудьте привітати клієнтів!</i>");

        return sb.toString();
    }

    private String formatClientName(PosterClientDto client) {
        if (client.getFirstName() != null && client.getLastName() != null) {
            return client.getFirstName() + " " + client.getLastName();
        }
        if (client.getFirstName() != null) {
            return client.getFirstName();
        }
        if (client.getLastName() != null) {
            return client.getLastName();
        }
        return "Клієнт #" + client.getClientId();
    }
}
