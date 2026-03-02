package org.jume.loyalitybot.dto.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionDto {

    @JsonProperty("transaction_id")
    private Long transactionId;

    @JsonProperty("client_id")
    private Long clientId;

    @JsonProperty("client_firstname")
    private String clientFirstName;

    @JsonProperty("client_lastname")
    private String clientLastName;

    @JsonProperty("date_close")
    private String dateClose;

    @JsonProperty("sum")
    private BigDecimal sum;

    @JsonProperty("payed_sum")
    private BigDecimal payedSum;

    @JsonProperty("payed_bonus")
    private BigDecimal payedBonus;

    @JsonProperty("bonus")
    private BigDecimal bonusEarned;

    @JsonProperty("discount")
    private BigDecimal discount;

    @JsonProperty("products")
    private List<TransactionProduct> products;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public String getClientFullName() {
        if (clientFirstName != null && clientLastName != null) {
            return clientFirstName + " " + clientLastName;
        }
        return clientFirstName != null ? clientFirstName : "Guest";
    }

    public String getFormattedDate() {
        if (dateClose == null || dateClose.isBlank()) {
            return "-";
        }
        try {
            // Try parsing as Unix timestamp (milliseconds)
            long timestamp = Long.parseLong(dateClose);
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.of("Europe/Kyiv")
            );
            return dateTime.format(DATE_FORMATTER);
        } catch (NumberFormatException e) {
            // Return raw value if can't parse
            return dateClose;
        }
    }

    // Convert from kopecks to hryvnia
    public BigDecimal getSumInHryvnia() {
        return toHryvnia(sum);
    }

    public BigDecimal getPayedSumInHryvnia() {
        return toHryvnia(payedSum);
    }

    public BigDecimal getPayedBonusInHryvnia() {
        return toHryvnia(payedBonus);
    }

    /**
     * Calculate actual bonus earned based on sum and bonus percentage.
     * Poster returns bonus as percentage (e.g., 3 means 3%), not as kopecks.
     */
    public BigDecimal getBonusEarnedInHryvnia() {
        if (bonusEarned == null || sum == null) return BigDecimal.ZERO;
        // bonusEarned is the percentage (e.g., 3 for 3%)
        // sum is in kopecks, so we calculate: (sum / 100) * (bonusEarned / 100)
        return sum.multiply(bonusEarned)
                .divide(BigDecimal.valueOf(10000), 2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getDiscountInHryvnia() {
        return toHryvnia(discount);
    }

    private BigDecimal toHryvnia(BigDecimal kopecks) {
        if (kopecks == null) return BigDecimal.ZERO;
        return kopecks.divide(HUNDRED, 2, java.math.RoundingMode.HALF_UP);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionProduct {
        @JsonProperty("product_id")
        private Long productId;

        @JsonProperty("product_name")
        private String productName;

        @JsonProperty("count")
        private Integer count;

        @JsonProperty("price")
        private BigDecimal price;

        public BigDecimal getPriceInHryvnia() {
            if (price == null) return BigDecimal.ZERO;
            return price.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        }
    }
}
