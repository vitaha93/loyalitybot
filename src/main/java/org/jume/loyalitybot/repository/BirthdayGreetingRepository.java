package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.BirthdayGreeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BirthdayGreetingRepository extends JpaRepository<BirthdayGreeting, Long> {

    boolean existsByCustomerIdAndGreetingYear(Long customerId, Integer greetingYear);
}
