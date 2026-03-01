package org.jume.loyalitybot.dto.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinanceTransactionDto {

    @JsonProperty("transaction_id")
    private Long transactionId;

    @JsonProperty("account_id")
    private Long accountId;

    @JsonProperty("account_name")
    private String accountName;

    @JsonProperty("category_id")
    private Long categoryId;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("type")
    private Integer type; // 0 = expense, 1 = income

    @JsonProperty("amount")
    private Long amountKopecks;

    @JsonProperty("balance")
    private Long balanceKopecks;

    @JsonProperty("date")
    private String date;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("supplier_name")
    private String supplierName;

    @JsonProperty("currency_symbol")
    private String currencySymbol;

    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public BigDecimal getAmountInHryvnia() {
        if (amountKopecks == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(amountKopecks).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getAbsoluteAmountInHryvnia() {
        return getAmountInHryvnia().abs();
    }

    public boolean isIncome() {
        return type != null && type == 1;
    }

    public boolean isExpense() {
        return type != null && type == 0;
    }

    public String getFormattedDate() {
        if (date == null || date.isBlank()) return "-";
        try {
            LocalDateTime dateTime = LocalDateTime.parse(date, INPUT_FORMATTER);
            return dateTime.format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            return date;
        }
    }

    public LocalDateTime getParsedDate() {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDateTime.parse(date, INPUT_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}
