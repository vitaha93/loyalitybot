package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.PosterTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PosterTransactionRepository extends JpaRepository<PosterTransaction, Long> {

    Optional<PosterTransaction> findByPosterTransactionId(Long posterTransactionId);

    boolean existsByPosterTransactionId(Long posterTransactionId);

    List<PosterTransaction> findByPosterClientIdOrderByTransactionDateDesc(Long posterClientId);

    @Query("SELECT t FROM PosterTransaction t WHERE t.posterClientId = :clientId " +
           "AND t.transactionDate >= :dateFrom ORDER BY t.transactionDate DESC")
    List<PosterTransaction> findByClientIdAndDateFrom(
        @Param("clientId") Long clientId,
        @Param("dateFrom") LocalDateTime dateFrom);

    @Query("SELECT MAX(t.transactionDate) FROM PosterTransaction t")
    Optional<LocalDateTime> findMaxTransactionDate();

    @Query("SELECT t FROM PosterTransaction t LEFT JOIN FETCH t.products " +
           "WHERE t.posterClientId = :clientId ORDER BY t.transactionDate DESC")
    List<PosterTransaction> findByClientIdWithProducts(@Param("clientId") Long clientId);
}
