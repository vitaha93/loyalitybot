package org.jume.loyalitybot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.CacheConfig;
import org.jume.loyalitybot.config.PosterApiConfig;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.jume.loyalitybot.dto.admin.FinanceTransactionDto;
import org.jume.loyalitybot.dto.admin.ProductDto;
import org.jume.loyalitybot.dto.admin.TransactionDto;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosterApiService {

    private final RestClient posterRestClient;
    private final PosterApiConfig config;
    private final ObjectMapper objectMapper;

    @Cacheable(value = CacheConfig.POSTER_CLIENT_CACHE, key = "#phone", unless = "#result == null")
    public Optional<PosterClientDto> findClientByPhone(String phone) {
        log.debug("Searching for client by phone: {}", phone);
        try {
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/clients.getClients")
                            .queryParam("token", config.getToken())
                            .queryParam("phone", normalizePhone(phone))
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (responseNode.isArray() && !responseNode.isEmpty()) {
                PosterClientDto client = objectMapper.treeToValue(responseNode.get(0), PosterClientDto.class);
                log.info("Found client in Poster: {}", client.getClientId());
                return Optional.of(client);
            }

            log.debug("No client found for phone: {}", phone);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error searching for client by phone: {}", phone, e);
            return Optional.empty();
        }
    }

    @Cacheable(value = CacheConfig.POSTER_CLIENT_CACHE, key = "#clientId")
    public Optional<PosterClientDto> getClient(Long clientId) {
        log.debug("Getting client by ID: {}", clientId);
        try {
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/clients.getClient")
                            .queryParam("token", config.getToken())
                            .queryParam("client_id", clientId)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (!responseNode.isMissingNode() && !responseNode.isNull()) {
                // Poster API returns array even for single client
                if (responseNode.isArray() && !responseNode.isEmpty()) {
                    PosterClientDto client = objectMapper.treeToValue(responseNode.get(0), PosterClientDto.class);
                    return Optional.of(client);
                } else if (!responseNode.isArray()) {
                    PosterClientDto client = objectMapper.treeToValue(responseNode, PosterClientDto.class);
                    return Optional.of(client);
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("Error getting client by ID: {}", clientId, e);
            return Optional.empty();
        }
    }

    @CacheEvict(value = CacheConfig.POSTER_CLIENT_CACHE, allEntries = true)
    public Optional<Long> createClient(String firstName, String lastName, String phone) {
        return createClient(firstName, lastName, phone, config.getDefaultClientGroupId());
    }

    @CacheEvict(value = CacheConfig.POSTER_CLIENT_CACHE, allEntries = true)
    public Optional<Long> createClient(String firstName, String lastName, String phone, Long clientGroupId) {
        String clientName = (firstName + " " + lastName).trim();
        String normalizedPhone = normalizePhone(phone);
        log.info("Creating new client in Poster: name='{}', phone='{}', groupId: {}", clientName, normalizedPhone, clientGroupId);
        try {
            // Build form data
            StringBuilder formData = new StringBuilder();
            formData.append("client_name=").append(java.net.URLEncoder.encode(clientName, java.nio.charset.StandardCharsets.UTF_8));
            formData.append("&client_phone=").append(java.net.URLEncoder.encode(normalizedPhone, java.nio.charset.StandardCharsets.UTF_8));
            if (clientGroupId != null) {
                formData.append("&client_groups_id=").append(clientGroupId);
            }

            String response = posterRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/clients.createClient")
                            .queryParam("token", config.getToken())
                            .build())
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (!responseNode.isMissingNode() && responseNode.has("client_id")) {
                Long clientId = responseNode.get("client_id").asLong();
                log.info("Created new client in Poster with ID: {}", clientId);
                return Optional.of(clientId);
            }

            log.warn("Failed to create client in Poster - unexpected response: {}", response);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error creating client in Poster", e);
            return Optional.empty();
        }
    }

    @CacheEvict(value = CacheConfig.POSTER_CLIENT_CACHE, key = "#clientId")
    public boolean updateClientBirthday(Long clientId, String birthday) {
        log.info("Updating birthday for client {}: {}", clientId, birthday);
        try {
            String response = posterRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/clients.updateClient")
                            .queryParam("token", config.getToken())
                            .queryParam("client_id", clientId)
                            .queryParam("birthday", birthday)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            boolean success = root.has("response") && !root.path("response").isNull();

            if (success) {
                log.info("Successfully updated birthday for client {}", clientId);
            } else {
                log.warn("Failed to update birthday for client {}: {}", clientId, response);
            }
            return success;
        } catch (Exception e) {
            log.error("Error updating birthday for client {}", clientId, e);
            return false;
        }
    }

    @Cacheable(value = CacheConfig.POSTER_BONUS_CACHE, key = "#clientId")
    public Optional<BigDecimal> getClientBonus(Long clientId) {
        log.debug("Getting bonus for client: {}", clientId);
        return getClient(clientId)
                .map(PosterClientDto::getBonusInHryvnia);
    }

    @CacheEvict(value = {CacheConfig.POSTER_CLIENT_CACHE, CacheConfig.POSTER_BONUS_CACHE}, key = "#clientId")
    public boolean addBonus(Long clientId, BigDecimal amount, String comment) {
        log.info("Adding bonus {} to client {}: {}", amount, clientId, comment);
        try {
            String response = posterRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/clients.changeClientBonus")
                            .queryParam("token", config.getToken())
                            .queryParam("client_id", clientId)
                            .queryParam("count", amount.intValue())
                            .queryParam("block_activate_id", 0)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            boolean success = root.has("response") && !root.path("response").isNull();

            if (success) {
                log.info("Successfully added {} bonus to client {}", amount, clientId);
            } else {
                log.warn("Failed to add bonus to client {}", clientId);
            }
            return success;
        } catch (Exception e) {
            log.error("Error adding bonus to client {}", clientId, e);
            return false;
        }
    }

    public boolean healthCheck() {
        try {
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/access.getTablets")
                            .queryParam("token", config.getToken())
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return !root.has("error");
        } catch (Exception e) {
            log.error("Poster API health check failed", e);
            return false;
        }
    }

    @Cacheable(value = CacheConfig.POSTER_CLIENTS_CACHE, unless = "#result.isEmpty()")
    public List<PosterClientDto> getAllClients() {
        log.debug("Fetching all clients from Poster");
        try {
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/clients.getClients")
                            .queryParam("token", config.getToken())
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (responseNode.isArray()) {
                List<PosterClientDto> clients = new ArrayList<>();
                for (JsonNode clientNode : responseNode) {
                    clients.add(objectMapper.treeToValue(clientNode, PosterClientDto.class));
                }
                log.info("Fetched {} clients from Poster", clients.size());
                return clients;
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching all clients from Poster", e);
            return Collections.emptyList();
        }
    }

    @Cacheable(value = CacheConfig.POSTER_TRANSACTIONS_CACHE, key = "#dateFrom.toString() + '_' + #dateTo.toString()")
    public List<TransactionDto> getAllTransactions(LocalDate dateFrom, LocalDate dateTo) {
        log.debug("Fetching transactions from {} to {}", dateFrom, dateTo);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dash.getTransactions")
                            .queryParam("token", config.getToken())
                            .queryParam("date_from", dateFrom.format(formatter))
                            .queryParam("date_to", dateTo.format(formatter))
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (responseNode.isArray()) {
                List<TransactionDto> transactions = new ArrayList<>();
                for (JsonNode txNode : responseNode) {
                    transactions.add(objectMapper.treeToValue(txNode, TransactionDto.class));
                }
                log.info("Fetched {} transactions from {} to {}", transactions.size(), dateFrom, dateTo);
                return transactions;
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching transactions from Poster", e);
            return Collections.emptyList();
        }
    }

    public List<TransactionDto> getClientTransactions(Long clientId, LocalDate dateFrom, LocalDate dateTo) {
        log.debug("Fetching transactions for client {} from {} to {}", clientId, dateFrom, dateTo);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dash.getTransactions")
                            .queryParam("token", config.getToken())
                            .queryParam("client_id", clientId)
                            .queryParam("date_from", dateFrom.format(formatter))
                            .queryParam("date_to", dateTo.format(formatter))
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (responseNode.isArray()) {
                List<TransactionDto> transactions = new ArrayList<>();
                for (JsonNode txNode : responseNode) {
                    transactions.add(objectMapper.treeToValue(txNode, TransactionDto.class));
                }
                return transactions;
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching client {} transactions from Poster", clientId, e);
            return Collections.emptyList();
        }
    }

    @Cacheable(value = CacheConfig.POSTER_PRODUCTS_CACHE, unless = "#result.isEmpty()")
    public List<ProductDto> getProducts() {
        log.debug("Fetching products from Poster");
        try {
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/menu.getProducts")
                            .queryParam("token", config.getToken())
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (responseNode.isArray()) {
                List<ProductDto> products = new ArrayList<>();
                for (JsonNode productNode : responseNode) {
                    products.add(objectMapper.treeToValue(productNode, ProductDto.class));
                }
                log.info("Fetched {} products from Poster", products.size());
                return products;
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching products from Poster", e);
            return Collections.emptyList();
        }
    }

    @CacheEvict(value = CacheConfig.POSTER_CLIENT_CACHE, key = "#clientId")
    public boolean setClientDiscount(Long clientId, Integer discountPercent) {
        log.info("Setting {}% discount for client {}", discountPercent, clientId);
        try {
            String response = posterRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/clients.updateClient")
                            .queryParam("token", config.getToken())
                            .queryParam("client_id", clientId)
                            .queryParam("discount_per", discountPercent)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            boolean success = root.has("response") && !root.path("response").isNull();

            if (success) {
                log.info("Successfully set {}% discount for client {}", discountPercent, clientId);
            } else {
                log.warn("Failed to set discount for client {}", clientId);
            }
            return success;
        } catch (Exception e) {
            log.error("Error setting discount for client {}", clientId, e);
            return false;
        }
    }

    /**
     * Fetch products for a specific transaction
     */
    @Cacheable(value = CacheConfig.POSTER_TX_PRODUCTS_CACHE, key = "#transactionId")
    public List<TransactionDto.TransactionProduct> getTransactionProducts(Long transactionId) {
        log.debug("Fetching products for transaction {}", transactionId);
        try {
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dash.getTransactionProducts")
                            .queryParam("token", config.getToken())
                            .queryParam("transaction_id", transactionId)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (responseNode.isArray()) {
                List<TransactionDto.TransactionProduct> products = new ArrayList<>();
                for (JsonNode productNode : responseNode) {
                    products.add(objectMapper.treeToValue(productNode, TransactionDto.TransactionProduct.class));
                }
                return products;
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching products for transaction {}", transactionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get client transactions with products (limited to recent transactions for performance)
     */
    public List<TransactionDto> getClientTransactionsWithProducts(Long clientId, LocalDate dateFrom, LocalDate dateTo, int limit) {
        List<TransactionDto> transactions = getClientTransactions(clientId, dateFrom, dateTo);
        log.info("Fetched {} transactions for client {} from {} to {}", transactions.size(), clientId, dateFrom, dateTo);

        // Sort by date descending (most recent first) and limit
        return transactions.stream()
                .sorted((a, b) -> {
                    if (a.getDateClose() == null) return 1;
                    if (b.getDateClose() == null) return -1;
                    return b.getDateClose().compareTo(a.getDateClose());
                })
                .limit(limit)
                .peek(tx -> {
                    if (tx.getTransactionId() != null) {
                        tx.setProducts(getTransactionProducts(tx.getTransactionId()));
                    }
                })
                .toList();
    }

    /**
     * Get finance transactions for P&L report
     */
    public List<FinanceTransactionDto> getFinanceTransactions(LocalDate dateFrom, LocalDate dateTo) {
        log.debug("Fetching finance transactions from {} to {}", dateFrom, dateTo);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/finance.getTransactions")
                            .queryParam("token", config.getToken())
                            .queryParam("dateFrom", dateFrom.format(formatter))
                            .queryParam("dateTo", dateTo.format(formatter))
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (responseNode.isArray()) {
                List<FinanceTransactionDto> transactions = new ArrayList<>();
                for (JsonNode txNode : responseNode) {
                    transactions.add(objectMapper.treeToValue(txNode, FinanceTransactionDto.class));
                }
                log.info("Fetched {} finance transactions from {} to {}", transactions.size(), dateFrom, dateTo);
                return transactions;
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching finance transactions from Poster", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get sales analytics summary
     */
    public Optional<SalesAnalytics> getSalesAnalytics(LocalDate dateFrom, LocalDate dateTo) {
        log.debug("Fetching sales analytics from {} to {}", dateFrom, dateTo);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            String response = posterRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dash.getAnalytics")
                            .queryParam("token", config.getToken())
                            .queryParam("dateFrom", dateFrom.format(formatter))
                            .queryParam("dateTo", dateTo.format(formatter))
                            .queryParam("type", "sales")
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");
            JsonNode counters = responseNode.path("counters");

            if (!counters.isMissingNode()) {
                SalesAnalytics analytics = new SalesAnalytics();
                analytics.setRevenue(new java.math.BigDecimal(counters.path("revenue").asText("0")));
                analytics.setProfit(new java.math.BigDecimal(counters.path("profit").asText("0")));
                analytics.setTransactions(counters.path("transactions").asInt(0));
                analytics.setAverageReceipt(new java.math.BigDecimal(String.valueOf(counters.path("average_receipt").asDouble(0))));

                // Parse daily data
                JsonNode dataNode = responseNode.path("data");
                if (dataNode.isArray()) {
                    List<java.math.BigDecimal> dailyRevenue = new ArrayList<>();
                    for (JsonNode day : dataNode) {
                        dailyRevenue.add(new java.math.BigDecimal(day.asText("0")));
                    }
                    analytics.setDailyRevenue(dailyRevenue);
                }

                return Optional.of(analytics);
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching sales analytics from Poster", e);
            return Optional.empty();
        }
    }

    @lombok.Data
    public static class SalesAnalytics {
        private java.math.BigDecimal revenue;
        private java.math.BigDecimal profit;
        private int transactions;
        private java.math.BigDecimal averageReceipt;
        private List<java.math.BigDecimal> dailyRevenue;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String normalized = phone.replaceAll("[^0-9]", "");
        if (normalized.startsWith("0") && normalized.length() == 10) {
            normalized = "38" + normalized;
        }
        return normalized;
    }
}
