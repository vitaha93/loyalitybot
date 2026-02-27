package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.model.Customer.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.telegramUsername) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "CAST(c.telegramId AS string) LIKE CONCAT('%', :query, '%')")
    Page<Customer> searchCustomers(@Param("query") String query, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.telegramUsername) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Customer> searchCustomersForSelect(@Param("query") String query, Pageable pageable);
}
