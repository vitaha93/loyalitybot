package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.model.PersonalDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonalDiscountRepository extends JpaRepository<PersonalDiscount, Long> {

    List<PersonalDiscount> findByCustomer(Customer customer);

    List<PersonalDiscount> findByCustomerAndActiveTrue(Customer customer);

    @Query("SELECT pd FROM PersonalDiscount pd WHERE pd.customer = :customer " +
           "AND pd.active = true AND :date BETWEEN pd.validFrom AND pd.validUntil")
    Optional<PersonalDiscount> findActiveDiscountForCustomer(
        @Param("customer") Customer customer,
        @Param("date") LocalDate date);

    @Query("SELECT pd FROM PersonalDiscount pd WHERE pd.active = true " +
           "AND :date BETWEEN pd.validFrom AND pd.validUntil " +
           "ORDER BY pd.validUntil ASC")
    List<PersonalDiscount> findActiveDiscounts(@Param("date") LocalDate date);

    @Query("SELECT pd FROM PersonalDiscount pd WHERE pd.active = true " +
           "AND pd.validUntil < :date " +
           "ORDER BY pd.validUntil DESC")
    List<PersonalDiscount> findExpiredDiscounts(@Param("date") LocalDate date);

    @Query("SELECT pd FROM PersonalDiscount pd WHERE pd.active = true " +
           "AND pd.validUntil = :date")
    List<PersonalDiscount> findDiscountsExpiringOn(@Param("date") LocalDate date);
}
