package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.PosterTransactionProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosterTransactionProductRepository extends JpaRepository<PosterTransactionProduct, Long> {

    @Query("SELECT p.posterProductId, p.productName, SUM(p.count) as totalCount " +
           "FROM PosterTransactionProduct p " +
           "WHERE p.posterClientId = :clientId " +
           "GROUP BY p.posterProductId, p.productName " +
           "ORDER BY totalCount DESC")
    List<Object[]> findTopProductsByClientId(@Param("clientId") Long clientId);

    @Query("SELECT p.posterProductId, p.productName, SUM(p.count) as totalCount " +
           "FROM PosterTransactionProduct p " +
           "WHERE p.posterClientId = :clientId " +
           "GROUP BY p.posterProductId, p.productName " +
           "ORDER BY totalCount DESC " +
           "LIMIT :limit")
    List<Object[]> findTopProductsByClientId(@Param("clientId") Long clientId, @Param("limit") int limit);
}
