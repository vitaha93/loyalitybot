package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.model.Customer.CustomerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByTelegramId(Long telegramId);

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByPosterClientId(Long posterClientId);

    List<Customer> findByStatus(CustomerStatus status);

    List<Customer> findByStatusIn(List<CustomerStatus> statuses);

    boolean existsByTelegramId(Long telegramId);

    boolean existsByPhone(String phone);

    long countByStatus(CustomerStatus status);
}
