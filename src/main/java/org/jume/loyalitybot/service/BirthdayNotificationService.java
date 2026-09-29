package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.AdminConfig;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.model.BirthdayGreeting;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.model.Customer.CustomerStatus;
import org.jume.loyalitybot.repository.BirthdayGreetingRepository;
import org.jume.loyalitybot.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class BirthdayNotificationService {

    private final PosterApiService posterApiService;
    private final TelegramBotService telegramBotService;
    private final CustomerRepository customerRepository;
    private final BirthdayGreetingRepository birthdayGreetingRepository;
    private final AdminConfig adminConfig;

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final long SEND_DELAY_MS = 50;
    private static final BigDecimal SAMPLE_BONUS = BigDecimal.valueOf(50);
    private static final BigDecimal SAMPLE_BALANCE = BigDecimal.valueOf(120);

    private static final List<DateTimeFormatter> BIRTHDAY_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd")  // Some systems store just month-day
    );

    /**
     * Runs every day at 8:00 AM Kyiv time — before the shift starts.
     * Sends admins the list of today's birthdays so the barista can greet
     * the clients who are not in the bot in person.
     */
    @Scheduled(cron = "${scheduling.birthday-digest-cron:0 0 8 * * *}", zone = "Europe/Kyiv")
    public void checkBirthdays() {
        log.info("Starting birthday check");

        Set<Long> adminIds = adminConfig.getAdminTelegramIds();
        if (adminIds.isEmpty()) {
            log.warn("No admin telegram IDs configured, skipping birthday notifications");
            return;
        }

        List<PosterClientDto> birthdayClients = findTodaysBirthdayClients();

        if (birthdayClients.isEmpty()) {
            log.info("No birthdays today");
            return;
        }

        log.info("Found {} clients with birthdays today", birthdayClients.size());

        notifyAdmins(formatBirthdayMessage(birthdayClients));
    }

    /**
     * Runs every day at 9:00 AM Kyiv time — the start of the morning peak.
     * Greets today's birthday clients who are in the bot and reports back to the admins.
     * <p>
     * The bonus itself is <b>not</b> granted here: Poster credits the birthday bonus
     * configured on the client group just after midnight. The bot only tells the
     * customer about it, using the amount Poster actually has configured.
     * <p>
     * Every greeting is recorded in {@code birthday_greetings} with a unique
     * (customer, year) key, so a restart or a second run never greets — or pays — twice.
     */
    @Scheduled(cron = "${scheduling.birthday-greeting-cron:0 0 9 * * *}", zone = "Europe/Kyiv")
    public void greetBirthdayCustomers() {
        log.info("Starting birthday greetings");

        List<PosterClientDto> birthdayClients = findTodaysBirthdayClients();
        if (birthdayClients.isEmpty()) {
            log.info("No birthdays today, nothing to greet");
            return;
        }

        Map<Long, BigDecimal> groupBonuses = posterApiService.getClientGroupBirthdayBonuses();
        int year = LocalDate.now(KYIV).getYear();
        int sent = 0;
        int failed = 0;
        int notInBot = 0;

        for (PosterClientDto client : birthdayClients) {
            Optional<Customer> customerOpt = findGreetableCustomer(client);
            if (customerOpt.isEmpty()) {
                notInBot++;
                continue;
            }

            Customer customer = customerOpt.get();
            Optional<BirthdayGreeting> greetingOpt = reserveGreeting(customer, year);
            if (greetingOpt.isEmpty()) {
                log.info("Customer {} already greeted in {}, skipping", customer.getId(), year);
                continue;
            }

            BigDecimal bonus = groupBonuses.getOrDefault(client.getClientGroupsId(), BigDecimal.ZERO);
            if (greetCustomer(customer, greetingOpt.get(), bonus)) {
                sent++;
            } else {
                failed++;
            }

            try {
                Thread.sleep(SEND_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Birthday greeting job interrupted");
                break;
            }
        }

        log.info("Birthday greetings finished: sent={}, failed={}, notInBot={}", sent, failed, notInBot);

        if (sent > 0 || failed > 0) {
            notifyAdmins(formatGreetingSummary(sent, failed, notInBot));
        }
    }

    /**
     * Creates the greeting record before any money or message goes out.
     * The unique (customer, year) constraint is what makes the job safe to re-run.
     */
    private Optional<BirthdayGreeting> reserveGreeting(Customer customer, int year) {
        if (birthdayGreetingRepository.existsByCustomerIdAndGreetingYear(customer.getId(), year)) {
            return Optional.empty();
        }

        BirthdayGreeting greeting = BirthdayGreeting.builder()
                .customer(customer)
                .posterClientId(customer.getPosterClientId())
                .greetingYear(year)
                .build();

        try {
            return Optional.of(birthdayGreetingRepository.saveAndFlush(greeting));
        } catch (DataIntegrityViolationException e) {
            // Another instance reserved the same customer between the check and the insert
            log.info("Greeting for customer {} in {} already reserved elsewhere", customer.getId(), year);
            return Optional.empty();
        }
    }

    /**
     * Sends the greeting, reporting the bonus Poster has already credited for this
     * client's group. A group without a birthday bonus gets a greeting with no gift
     * line, so we never announce money that was not given.
     */
    private boolean greetCustomer(Customer customer, BirthdayGreeting greeting, BigDecimal bonus) {
        boolean hasBonus = bonus != null && bonus.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal totalBonus = null;

        if (hasBonus && customer.getPosterClientId() != null) {
            totalBonus = posterApiService.getClientBonus(customer.getPosterClientId()).orElse(null);
        }

        greeting.setBonusAmount(hasBonus ? bonus : BigDecimal.ZERO);

        boolean delivered = telegramBotService.sendBirthdayGreeting(
                customer.getTelegramId(),
                greetingName(customer),
                hasBonus ? bonus : BigDecimal.ZERO,
                totalBonus);

        if (delivered) {
            greeting.markAsSent(hasBonus);
            log.info("Sent birthday greeting to customer {} (bonus reported: {})", customer.getId(), hasBonus);
        } else {
            greeting.markAsFailed("Telegram не прийняв повідомлення");
            log.warn("Failed to deliver birthday greeting to customer {}", customer.getId());
        }

        birthdayGreetingRepository.save(greeting);
        return delivered;
    }

    /**
     * Only active bot customers can be greeted — clients that exist in Poster
     * but never finished registration have no usable chat.
     */
    private Optional<Customer> findGreetableCustomer(PosterClientDto client) {
        if (client.getClientId() == null) {
            return Optional.empty();
        }
        return customerRepository.findByPosterClientId(client.getClientId())
                .filter(c -> c.getTelegramId() != null)
                .filter(c -> c.getStatus() == CustomerStatus.ACTIVE);
    }

    private String greetingName(Customer customer) {
        if (customer.getFirstName() != null && !customer.getFirstName().isBlank()) {
            return customer.getFirstName();
        }
        return customer.getDisplayName();
    }

    private void notifyAdmins(String message) {
        for (Long adminId : adminConfig.getAdminTelegramIds()) {
            try {
                telegramBotService.sendMessage(adminId, message);
                log.info("Sent birthday notification to admin {}", adminId);
            } catch (Exception e) {
                log.error("Failed to send birthday notification to admin {}: {}", adminId, e.getMessage());
            }
        }
    }

    public List<PosterClientDto> findTodaysBirthdayClients() {
        LocalDate today = LocalDate.now(KYIV);
        int todayMonth = today.getMonthValue();
        int todayDay = today.getDayOfMonth();

        List<PosterClientDto> birthdayClients = new ArrayList<>();
        for (PosterClientDto client : posterApiService.getAllClients()) {
            if (hasBirthdayToday(client.getBirthday(), todayMonth, todayDay)) {
                birthdayClients.add(client);
            }
        }
        return birthdayClients;
    }

    /**
     * Sends the greeting exactly as a customer would see it, without granting a bonus
     * and without recording anything — used from the admin panel to preview the text.
     */
    public boolean sendTestGreeting(Long chatId, String name) {
        log.info("Sending test birthday greeting to chat {}", chatId);
        return telegramBotService.sendBirthdayGreeting(chatId, name, SAMPLE_BONUS, SAMPLE_BALANCE);
    }

    /**
     * True when the customer behind this Poster client is an active bot user
     * who has not been greeted yet this year.
     */
    public boolean willBeGreetedToday(PosterClientDto client) {
        int year = LocalDate.now(KYIV).getYear();
        return findGreetableCustomer(client)
                .map(c -> !birthdayGreetingRepository.existsByCustomerIdAndGreetingYear(c.getId(), year))
                .orElse(false);
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
        int year = LocalDate.now(KYIV).getYear();
        StringBuilder sb = new StringBuilder();
        sb.append("<b>🎂 Дні народження сьогодні:</b>\n\n");

        int willBeGreeted = 0;
        int atCounter = 0;
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

            Optional<Customer> customer = findGreetableCustomer(client);
            boolean alreadyGreeted = customer
                    .map(c -> birthdayGreetingRepository.existsByCustomerIdAndGreetingYear(c.getId(), year))
                    .orElse(false);

            if (alreadyGreeted) {
                sb.append("  ✅ вже привітали в боті\n");
            } else if (customer.isPresent()) {
                willBeGreeted++;
                sb.append("  🤖 бот привітає о 9:00\n");
            } else {
                atCounter++;
                sb.append("  ☎️ не в боті — привітати на касі\n");
            }
        }

        sb.append("\n<i>Бот привітає ").append(willBeGreeted)
                .append(", вручну на касі: ").append(atCounter).append("</i>");

        return sb.toString();
    }

    private String formatGreetingSummary(int sent, int failed, int notInBot) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>🎂 Вітання надіслано</b>\n\n");
        sb.append("Привітано в боті: <b>").append(sent).append("</b>\n");
        if (failed > 0) {
            sb.append("Не доставлено: <b>").append(failed).append("</b> (бот заблокований?)\n");
        }
        if (notInBot > 0) {
            sb.append("Не в боті: <b>").append(notInBot).append("</b> — привітати на касі\n");
        }
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
