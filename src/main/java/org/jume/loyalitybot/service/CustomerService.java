package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.LoyaltyConfig;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.dto.TelegramUpdate.TelegramContact;
import org.jume.loyalitybot.dto.TelegramUpdate.TelegramUser;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.model.Customer.CustomerStatus;
import org.jume.loyalitybot.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PosterApiService posterApiService;
    private final TelegramBotService telegramBotService;
    private final LoyaltyConfig loyaltyConfig;

    @Transactional
    public Customer getOrCreateCustomer(TelegramUser telegramUser) {
        return customerRepository.findByTelegramId(telegramUser.getId())
                .orElseGet(() -> createNewCustomer(telegramUser));
    }

    private Customer createNewCustomer(TelegramUser telegramUser) {
        Customer customer = Customer.builder()
                .telegramId(telegramUser.getId())
                .telegramUsername(telegramUser.getUsername())
                .firstName(telegramUser.getFirstName())
                .lastName(telegramUser.getLastName())
                .status(CustomerStatus.PENDING_PHONE)
                .build();

        customer = customerRepository.save(customer);
        log.info("Created new customer: {} (telegramId: {})", customer.getDisplayName(), customer.getTelegramId());
        return customer;
    }

    @Transactional
    public Customer processPhoneRegistration(Long telegramId, TelegramContact contact) {
        Customer customer = customerRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalStateException("Customer not found for telegramId: " + telegramId));

        String phone = normalizePhone(contact.getPhoneNumber());
        customer.setPhone(phone);

        Optional<PosterClientDto> existingClient = posterApiService.findClientByPhone(phone);

        if (existingClient.isPresent()) {
            // Existing client in Poster - link and check if birthday is missing
            PosterClientDto posterClient = existingClient.get();
            linkToPosterClient(customer, posterClient);
            customer.setIsNewClient(false);

            boolean hasBirthday = posterClient.getBirthday() != null && !posterClient.getBirthday().isBlank()
                    && !"0000-00-00".equals(posterClient.getBirthday());

            if (hasBirthday) {
                customer.setStatus(CustomerStatus.ACTIVE);
                customer = customerRepository.save(customer);

                log.info("Linked customer {} to existing Poster client {}", customer.getTelegramId(), posterClient.getClientId());

                // Send welcome message for existing client (no bonus mention)
                telegramBotService.sendMessageWithMainMenu(telegramId,
                        String.format("Вітаємо, %s!\n\n" +
                                "Ви успішно приєднались до програми лояльності.\n" +
                                "Тепер ви можете отримувати сповіщення про бонуси.",
                                customer.getDisplayName()));
            } else {
                // Existing client but no birthday - ask for it
                customer.setStatus(CustomerStatus.PENDING_BIRTHDAY);
                customer = customerRepository.save(customer);

                log.info("Linked customer {} to existing Poster client {}, asking for birthday", customer.getTelegramId(), posterClient.getClientId());

                telegramBotService.sendMessage(telegramId,
                        String.format("Вітаємо, %s!\n\n" +
                                "Ви успішно приєднались до програми лояльності.\n\n" +
                                "Будь ласка, введіть дату вашого народження у форматі ДД.ММ.РРРР (наприклад, 25.12.1990):",
                                customer.getDisplayName()));
            }
        } else {
            // New client - create in Poster, ask for birthday
            createPosterClientAndLink(customer);
            customer.setIsNewClient(true);
            customer.setStatus(CustomerStatus.PENDING_BIRTHDAY);
            customer = customerRepository.save(customer);

            log.info("Created new Poster client for customer {}, asking for birthday", customer.getTelegramId());

            // Ask for birthday
            telegramBotService.sendMessage(telegramId,
                    String.format("Вітаємо, %s!\n\n" +
                            "Ви зареєстровані в програмі лояльності.\n" +
                            "Poster автоматично нарахував вам вітальний бонус 5 грн!\n\n" +
                            "Будь ласка, введіть дату вашого народження у форматі ДД.ММ.РРРР (наприклад, 25.12.1990):",
                            customer.getDisplayName()));
        }

        return customer;
    }

    @Transactional
    public Customer processBirthdayRegistration(Long telegramId, String birthdayInput) {
        Customer customer = customerRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalStateException("Customer not found for telegramId: " + telegramId));

        if (customer.getStatus() != CustomerStatus.PENDING_BIRTHDAY) {
            log.warn("Customer {} is not in PENDING_BIRTHDAY status", telegramId);
            return customer;
        }

        LocalDate birthday = parseBirthday(birthdayInput);
        if (birthday == null) {
            telegramBotService.sendMessage(telegramId,
                    "Невірний формат дати. Будь ласка, введіть дату у форматі ДД.ММ.РРРР (наприклад, 25.12.1990):");
            return customer;
        }

        // Validate birthday is not in the future and person is at least 5 years old
        LocalDate today = LocalDate.now();
        if (birthday.isAfter(today) || birthday.isAfter(today.minusYears(5))) {
            telegramBotService.sendMessage(telegramId,
                    "Будь ласка, введіть коректну дату народження:");
            return customer;
        }

        customer.setBirthday(birthday);
        customer.setStatus(CustomerStatus.ACTIVE);
        customer = customerRepository.save(customer);

        // Update birthday in Poster
        if (customer.getPosterClientId() != null) {
            String posterBirthdayFormat = birthday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            posterApiService.updateClientBirthday(customer.getPosterClientId(), posterBirthdayFormat);
        }

        log.info("Completed birthday registration for customer {}: {}", telegramId, birthday);

        telegramBotService.sendMessageWithMainMenu(telegramId,
                "Дякуємо! Реєстрацію завершено.\n\n" +
                "Використовуйте кнопки меню нижче, щоб перевірити баланс або отримати картку.");

        return customer;
    }

    private LocalDate parseBirthday(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String trimmed = input.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private void linkToPosterClient(Customer customer, PosterClientDto posterClient) {
        customer.setPosterClientId(posterClient.getClientId());
        if (customer.getFirstName() == null || customer.getFirstName().isBlank()) {
            customer.setFirstName(posterClient.getFirstName());
        }
        if (customer.getLastName() == null || customer.getLastName().isBlank()) {
            customer.setLastName(posterClient.getLastName());
        }
    }

    private void createPosterClientAndLink(Customer customer) {
        Optional<Long> posterClientId = posterApiService.createClient(
                customer.getFirstName() != null ? customer.getFirstName() : "",
                customer.getLastName() != null ? customer.getLastName() : "",
                customer.getPhone()
        );

        if (posterClientId.isPresent()) {
            customer.setPosterClientId(posterClientId.get());
            log.info("Created new Poster client {} for customer {}", posterClientId.get(), customer.getTelegramId());
        } else {
            log.warn("Failed to create Poster client for customer {}", customer.getTelegramId());
        }
    }

    public Optional<Customer> findByTelegramId(Long telegramId) {
        return customerRepository.findByTelegramId(telegramId);
    }

    public Optional<Customer> findByPosterClientId(Long posterClientId) {
        return customerRepository.findByPosterClientId(posterClientId);
    }

    public Optional<BigDecimal> getCustomerBalance(Long telegramId) {
        return customerRepository.findByTelegramId(telegramId)
                .filter(c -> c.getStatus() == CustomerStatus.ACTIVE)
                .filter(c -> c.getPosterClientId() != null)
                .flatMap(c -> posterApiService.getClientBonus(c.getPosterClientId()));
    }

    public List<Customer> getActiveCustomers() {
        return customerRepository.findByStatus(CustomerStatus.ACTIVE);
    }

    public long getActiveCustomersCount() {
        return customerRepository.countByStatus(CustomerStatus.ACTIVE);
    }

    public long getTotalCustomersCount() {
        return customerRepository.count();
    }

    public long getPendingCustomersCount() {
        return customerRepository.countByStatus(CustomerStatus.PENDING_PHONE);
    }

    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }

    public Page<Customer> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    public Page<Customer> searchCustomers(String query, Pageable pageable) {
        return customerRepository.searchCustomers(query, pageable);
    }

    public List<Customer> searchCustomersForSelect(String query, int limit) {
        return customerRepository.searchCustomersForSelect(query, PageRequest.of(0, limit));
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String normalized = phone.replaceAll("[^0-9]", "");
        if (normalized.startsWith("0") && normalized.length() == 10) {
            normalized = "38" + normalized;
        }
        return normalized;
    }
}
