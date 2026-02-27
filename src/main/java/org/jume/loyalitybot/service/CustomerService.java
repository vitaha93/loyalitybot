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
            linkToPosterClient(customer, existingClient.get());
            log.info("Linked customer {} to existing Poster client {}", customer.getTelegramId(), existingClient.get().getClientId());
        } else {
            createPosterClientAndLink(customer);
        }

        customer.setStatus(CustomerStatus.ACTIVE);
        customer = customerRepository.save(customer);

        BigDecimal welcomeBonus = loyaltyConfig.getWelcomeBonus();
        if (welcomeBonus.compareTo(BigDecimal.ZERO) > 0 && customer.getPosterClientId() != null) {
            posterApiService.addBonus(customer.getPosterClientId(), welcomeBonus, "Вітальний бонус");
        }

        telegramBotService.sendWelcomeMessage(telegramId, customer.getDisplayName(), welcomeBonus);

        return customer;
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
