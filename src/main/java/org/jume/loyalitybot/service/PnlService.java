package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.admin.FinanceTransactionDto;
import org.jume.loyalitybot.dto.admin.PnlReportDto;
import org.jume.loyalitybot.dto.admin.PnlReportDto.CategoryTotal;
import org.jume.loyalitybot.dto.admin.PnlReportDto.DailyData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PnlService {

    private final PosterApiService posterApiService;

    // Category IDs for classification
    private static final Set<Long> REVENUE_CATEGORIES = Set.of(2L); // Касові зміни
    private static final Set<Long> COGS_CATEGORIES = Set.of(3L); // Постачання
    private static final Set<Long> TRANSFER_CATEGORIES = Set.of(1L); // Перекази (exclude from P&L)
    private static final Set<Long> ADJUSTMENT_CATEGORIES = Set.of(4L); // Актуалізація
    private static final Set<Long> OWNER_WITHDRAWAL_CATEGORIES = Set.of(18L); // Вивід з обороту (profit for owner)

    public PnlReportDto generateReport(LocalDate dateFrom, LocalDate dateTo) {
        log.info("Generating P&L report from {} to {}", dateFrom, dateTo);

        List<FinanceTransactionDto> transactions = posterApiService.getFinanceTransactions(dateFrom, dateTo);
        log.info("Fetched {} finance transactions", transactions.size());

        // Filter out transfers (internal movements)
        List<FinanceTransactionDto> relevantTransactions = transactions.stream()
                .filter(tx -> !TRANSFER_CATEGORIES.contains(tx.getCategoryId()))
                .toList();

        // Calculate revenue (Касові зміни - income type)
        BigDecimal totalRevenue = relevantTransactions.stream()
                .filter(tx -> REVENUE_CATEGORIES.contains(tx.getCategoryId()) && tx.isIncome())
                .map(FinanceTransactionDto::getAmountInHryvnia)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate revenue by account
        Map<String, BigDecimal> revenueByAccount = relevantTransactions.stream()
                .filter(tx -> REVENUE_CATEGORIES.contains(tx.getCategoryId()) && tx.isIncome())
                .collect(Collectors.groupingBy(
                        FinanceTransactionDto::getAccountName,
                        Collectors.reducing(BigDecimal.ZERO, FinanceTransactionDto::getAmountInHryvnia, BigDecimal::add)
                ));

        // Calculate COGS (Постачання)
        BigDecimal totalCogs = relevantTransactions.stream()
                .filter(tx -> COGS_CATEGORIES.contains(tx.getCategoryId()))
                .map(FinanceTransactionDto::getAbsoluteAmountInHryvnia)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // COGS by supplier
        List<CategoryTotal> cogsBySupplier = relevantTransactions.stream()
                .filter(tx -> COGS_CATEGORIES.contains(tx.getCategoryId()))
                .collect(Collectors.groupingBy(
                        tx -> tx.getSupplierName() != null ? tx.getSupplierName() : tx.getComment(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    BigDecimal sum = list.stream()
                                            .map(FinanceTransactionDto::getAbsoluteAmountInHryvnia)
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    return CategoryTotal.builder()
                                            .categoryName(list.get(0).getSupplierName() != null ?
                                                    list.get(0).getSupplierName() : list.get(0).getComment())
                                            .amount(sum)
                                            .transactionCount(list.size())
                                            .build();
                                }
                        )
                ))
                .values().stream()
                .sorted(Comparator.comparing(CategoryTotal::getAmount).reversed())
                .toList();

        // Gross profit
        BigDecimal grossProfit = totalRevenue.subtract(totalCogs);

        // Operating expenses (all other expense categories except COGS, transfers, and owner withdrawals)
        List<FinanceTransactionDto> operatingExpenses = relevantTransactions.stream()
                .filter(tx -> !REVENUE_CATEGORIES.contains(tx.getCategoryId()))
                .filter(tx -> !COGS_CATEGORIES.contains(tx.getCategoryId()))
                .filter(tx -> !ADJUSTMENT_CATEGORIES.contains(tx.getCategoryId()))
                .filter(tx -> !OWNER_WITHDRAWAL_CATEGORIES.contains(tx.getCategoryId()))
                .filter(FinanceTransactionDto::isExpense)
                .toList();

        BigDecimal totalOperatingExpenses = operatingExpenses.stream()
                .map(FinanceTransactionDto::getAbsoluteAmountInHryvnia)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Expenses by category
        List<CategoryTotal> expensesByCategory = operatingExpenses.stream()
                .collect(Collectors.groupingBy(
                        FinanceTransactionDto::getCategoryName,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    BigDecimal sum = list.stream()
                                            .map(FinanceTransactionDto::getAbsoluteAmountInHryvnia)
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    return CategoryTotal.builder()
                                            .categoryId(list.get(0).getCategoryId())
                                            .categoryName(list.get(0).getCategoryName())
                                            .amount(sum)
                                            .transactionCount(list.size())
                                            .build();
                                }
                        )
                ))
                .values().stream()
                .sorted(Comparator.comparing(CategoryTotal::getAmount).reversed())
                .peek(cat -> {
                    if (totalOperatingExpenses.compareTo(BigDecimal.ZERO) > 0) {
                        cat.setPercentOfTotal(cat.getAmount()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(totalOperatingExpenses, 1, RoundingMode.HALF_UP));
                    }
                })
                .toList();

        // Net profit (before owner withdrawal)
        BigDecimal netProfit = grossProfit.subtract(totalOperatingExpenses);

        // Owner withdrawal (Вивід з обороту)
        BigDecimal ownerWithdrawal = relevantTransactions.stream()
                .filter(tx -> OWNER_WITHDRAWAL_CATEGORIES.contains(tx.getCategoryId()))
                .filter(FinanceTransactionDto::isExpense)
                .map(FinanceTransactionDto::getAbsoluteAmountInHryvnia)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Retained profit (what's left after owner takes their share)
        BigDecimal retainedProfit = netProfit.subtract(ownerWithdrawal);

        // Daily data for charts
        List<DailyData> dailyData = calculateDailyData(relevantTransactions, dateFrom, dateTo);

        // All categories summary
        List<CategoryTotal> allCategories = new ArrayList<>();
        allCategories.add(CategoryTotal.builder()
                .categoryName("Виручка")
                .amount(totalRevenue)
                .build());
        allCategories.add(CategoryTotal.builder()
                .categoryName("Собівартість (закупки)")
                .amount(totalCogs.negate())
                .build());
        allCategories.addAll(expensesByCategory.stream()
                .map(c -> CategoryTotal.builder()
                        .categoryId(c.getCategoryId())
                        .categoryName(c.getCategoryName())
                        .amount(c.getAmount().negate())
                        .transactionCount(c.getTransactionCount())
                        .build())
                .toList());

        return PnlReportDto.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .totalRevenue(totalRevenue)
                .revenueByAccount(revenueByAccount)
                .totalCogs(totalCogs)
                .cogsBySupplier(cogsBySupplier)
                .grossProfit(grossProfit)
                .totalOperatingExpenses(totalOperatingExpenses)
                .expensesByCategory(expensesByCategory)
                .netProfit(netProfit)
                .ownerWithdrawal(ownerWithdrawal)
                .retainedProfit(retainedProfit)
                .dailyData(dailyData)
                .allCategories(allCategories)
                .build();
    }

    private List<DailyData> calculateDailyData(List<FinanceTransactionDto> transactions,
                                                LocalDate dateFrom, LocalDate dateTo) {
        // Group transactions by date
        Map<LocalDate, List<FinanceTransactionDto>> byDate = transactions.stream()
                .filter(tx -> tx.getParsedDate() != null)
                .collect(Collectors.groupingBy(tx -> tx.getParsedDate().toLocalDate()));

        List<DailyData> result = new ArrayList<>();
        LocalDate current = dateFrom;

        while (!current.isAfter(dateTo)) {
            List<FinanceTransactionDto> dayTransactions = byDate.getOrDefault(current, Collections.emptyList());

            BigDecimal dayRevenue = dayTransactions.stream()
                    .filter(tx -> REVENUE_CATEGORIES.contains(tx.getCategoryId()) && tx.isIncome())
                    .map(FinanceTransactionDto::getAmountInHryvnia)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal dayExpenses = dayTransactions.stream()
                    .filter(tx -> !REVENUE_CATEGORIES.contains(tx.getCategoryId()))
                    .filter(tx -> !TRANSFER_CATEGORIES.contains(tx.getCategoryId()))
                    .filter(FinanceTransactionDto::isExpense)
                    .map(FinanceTransactionDto::getAbsoluteAmountInHryvnia)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.add(DailyData.builder()
                    .date(current)
                    .revenue(dayRevenue)
                    .expenses(dayExpenses)
                    .profit(dayRevenue.subtract(dayExpenses))
                    .build());

            current = current.plusDays(1);
        }

        return result;
    }
}
