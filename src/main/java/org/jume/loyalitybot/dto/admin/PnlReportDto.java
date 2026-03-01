package org.jume.loyalitybot.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PnlReportDto {

    private LocalDate dateFrom;
    private LocalDate dateTo;

    // Revenue
    private BigDecimal totalRevenue;
    private Map<String, BigDecimal> revenueByAccount; // by spot/account

    // Cost of Goods Sold (COGS)
    private BigDecimal totalCogs;
    private List<CategoryTotal> cogsBySupplier;

    // Gross Profit
    private BigDecimal grossProfit;
    private BigDecimal grossMarginPercent;

    // Operating Expenses
    private BigDecimal totalOperatingExpenses;
    private List<CategoryTotal> expensesByCategory;

    // Net Profit
    private BigDecimal netProfit;
    private BigDecimal netMarginPercent;

    // Daily data for charts
    private List<DailyData> dailyData;

    // Category breakdown
    private List<CategoryTotal> allCategories;

    @Data
    @Builder
    public static class CategoryTotal {
        private Long categoryId;
        private String categoryName;
        private BigDecimal amount;
        private int transactionCount;
        private BigDecimal percentOfTotal;
    }

    @Data
    @Builder
    public static class DailyData {
        private LocalDate date;
        private BigDecimal revenue;
        private BigDecimal expenses;
        private BigDecimal profit;
    }

    public BigDecimal getGrossMarginPercent() {
        if (totalRevenue == null || totalRevenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return grossProfit.multiply(BigDecimal.valueOf(100))
                .divide(totalRevenue, 1, RoundingMode.HALF_UP);
    }

    public BigDecimal getNetMarginPercent() {
        if (totalRevenue == null || totalRevenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return netProfit.multiply(BigDecimal.valueOf(100))
                .divide(totalRevenue, 1, RoundingMode.HALF_UP);
    }
}
