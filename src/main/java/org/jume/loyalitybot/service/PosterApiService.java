package org.jume.loyalitybot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.CacheConfig;
import org.jume.loyalitybot.config.PosterApiConfig;
import org.jume.loyalitybot.dto.PosterClientDto;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
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
        log.info("Creating new client in Poster: {} {} {}", firstName, lastName, phone);
        try {
            String response = posterRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/clients.createClient")
                            .queryParam("token", config.getToken())
                            .queryParam("client_name", (firstName + " " + lastName).trim())
                            .queryParam("client_phone", normalizePhone(phone))
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.path("response");

            if (!responseNode.isMissingNode() && responseNode.has("client_id")) {
                Long clientId = responseNode.get("client_id").asLong();
                log.info("Created new client in Poster with ID: {}", clientId);
                return Optional.of(clientId);
            }

            log.warn("Failed to create client in Poster - unexpected response");
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error creating client in Poster", e);
            return Optional.empty();
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

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String normalized = phone.replaceAll("[^0-9]", "");
        if (normalized.startsWith("0") && normalized.length() == 10) {
            normalized = "38" + normalized;
        }
        return normalized;
    }
}
