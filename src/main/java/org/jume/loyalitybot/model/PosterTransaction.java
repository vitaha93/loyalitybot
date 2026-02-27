package org.jume.loyalitybot.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "poster_transactions", indexes = {
    @Index(name = "idx_poster_tx_client_id", columnList = "poster_client_id"),
    @Index(name = "idx_poster_tx_date", columnList = "transaction_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosterTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poster_transaction_id", nullable = false, unique = true)
    private Long posterTransactionId;

    @Column(name = "poster_client_id")
    private Long posterClientId;

    @Column(name = "client_first_name")
    private String clientFirstName;

    @Column(name = "client_last_name")
    private String clientLastName;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(name = "date_close")
    private String dateClose;

    @Column(name = "sum")
    private BigDecimal sum;

    @Column(name = "payed_sum")
    private BigDecimal payedSum;

    @Column(name = "payed_bonus")
    private BigDecimal payedBonus;

    @Column(name = "bonus_earned")
    private BigDecimal bonusEarned;

    @Column(name = "discount")
    private BigDecimal discount;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PosterTransactionProduct> products = new ArrayList<>();

    @Column(name = "synced_at", nullable = false)
    @Builder.Default
    private LocalDateTime syncedAt = LocalDateTime.now();

    public void addProduct(PosterTransactionProduct product) {
        products.add(product);
        product.setTransaction(this);
    }

    public String getClientFullName() {
        if (clientFirstName != null && clientLastName != null) {
            return clientFirstName + " " + clientLastName;
        }
        return clientFirstName != null ? clientFirstName : "Guest";
    }
}
