package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.dto.admin.DashboardStats;
import org.jume.loyalitybot.dto.admin.ProductDto;
import org.jume.loyalitybot.dto.admin.TransactionDto;
import org.jume.loyalitybot.model.Customer;
import org.jume.loyalitybot.repository.PosterTransactionProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminStatsService {

    private final CustomerService customerService;
    private final PosterApiService posterApiService;
    private final PosterTransactionProductRepository productRepository;

    public DashboardStats getDashboardStats() {
        long totalCustomers = customerService.getTotalCustomersCount();
        long activeCustomers = customerService.getActiveCustomersCount();
        long pendingCustomers = customerService.getPendingCustomersCount();

        // Get transactions for statistics
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);
        LocalDate monthAgo = today.minusDays(30);

        List<TransactionDto> todayTransactions = posterApiService.getAllTransactions(today, today);
        List<TransactionDto> weekTransactions = posterApiService.getAllTransactions(weekAgo, today);
        List<TransactionDto> monthTransactions = posterApiService.getAllTransactions(monthAgo, today);

        BigDecimal totalRevenue = monthTransactions.stream()
            .map(TransactionDto::getPayedSumInHryvnia)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardStats.builder()
            .totalCustomers(totalCustomers)
            .activeCustomers(activeCustomers)
            .pendingCustomers(pendingCustomers)
            .totalRevenue(totalRevenue)
            .transactionsToday(todayTransactions.size())
            .transactionsThisWeek(weekTransactions.size())
            .transactionsThisMonth(monthTransactions.size())
            .build();
    }

    public List<CustomerWithStats> getTopCustomersBySpending(int limit) {
        List<PosterClientDto> allClients = posterApiService.getAllClients();

        return allClients.stream()
            .filter(client -> client.getTotalPayedSum() != null)
            .sorted(Comparator.comparing(PosterClientDto::getTotalPayedSum).reversed())
            .limit(limit)
            .map(client -> {
                Customer customer = customerService.findByPosterClientId(client.getClientId()).orElse(null);
                return new CustomerWithStats(client, customer);
            })
            .collect(Collectors.toList());
    }

    public List<TransactionDto> getRecentTransactions(int limit) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);

        List<TransactionDto> transactions = posterApiService.getAllTransactions(weekAgo, today);

        return transactions.stream()
            .sorted(Comparator.comparing(TransactionDto::getDateClose, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit)
            .collect(Collectors.toList());
    }

    public List<TransactionDto> getTransactions(LocalDate dateFrom, LocalDate dateTo, int page, int size) {
        List<TransactionDto> allTransactions = posterApiService.getAllTransactions(dateFrom, dateTo);

        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, allTransactions.size());

        if (fromIndex >= allTransactions.size()) {
            return Collections.emptyList();
        }

        return allTransactions.stream()
            .sorted(Comparator.comparing(TransactionDto::getDateClose, Comparator.nullsLast(Comparator.reverseOrder())))
            .skip(fromIndex)
            .limit(size)
            .collect(Collectors.toList());
    }

    public List<TransactionDto> getClientTransactions(Long clientId, int limit) {
        LocalDate today = LocalDate.now();
        LocalDate yearAgo = today.minusYears(1);

        return posterApiService.getClientTransactions(clientId, yearAgo, today).stream()
            .sorted(Comparator.comparing(TransactionDto::getDateClose, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit)
            .collect(Collectors.toList());
    }

    public List<FavoriteProduct> getClientFavoriteProducts(Long clientId, int limit) {
        // Query from local database (synced from Poster)
        List<Object[]> results = productRepository.findTopProductsByClientId(clientId, limit);

        if (results.isEmpty()) {
            log.debug("No products found in local DB for client {}. Data may not be synced yet.", clientId);
            return Collections.emptyList();
        }

        return results.stream()
            .map(row -> new FavoriteProduct(
                ((Number) row[0]).longValue(),  // productId
                (String) row[1],                 // productName
                ((Number) row[2]).intValue()     // count
            ))
            .collect(Collectors.toList());
    }

    public record CustomerWithStats(PosterClientDto posterClient, Customer customer) {
        public String getDisplayName() {
            if (customer != null) {
                return customer.getDisplayName();
            }
            if (posterClient.getFirstName() != null) {
                return posterClient.getLastName() != null
                    ? posterClient.getFirstName() + " " + posterClient.getLastName()
                    : posterClient.getFirstName();
            }
            return "Client #" + posterClient.getClientId();
        }
    }

    public record FavoriteProduct(Long productId, String name, int count) {}

    private record ProductCount(String name, int count) {}
}
