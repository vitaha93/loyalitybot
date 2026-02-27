package org.jume.loyalitybot.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "poster_transaction_products", indexes = {
    @Index(name = "idx_poster_tx_prod_client", columnList = "poster_client_id"),
    @Index(name = "idx_poster_tx_prod_product", columnList = "poster_product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosterTransactionProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private PosterTransaction transaction;

    @Column(name = "poster_client_id")
    private Long posterClientId;

    @Column(name = "poster_product_id")
    private Long posterProductId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "count")
    private Integer count;

    @Column(name = "price")
    private BigDecimal price;
}
