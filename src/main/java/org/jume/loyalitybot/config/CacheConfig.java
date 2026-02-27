package org.jume.loyalitybot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String POSTER_CLIENT_CACHE = "posterClient";
    public static final String POSTER_BONUS_CACHE = "posterBonus";
    public static final String POSTER_CLIENTS_CACHE = "posterClients";
    public static final String POSTER_TRANSACTIONS_CACHE = "posterTransactions";
    public static final String POSTER_PRODUCTS_CACHE = "posterProducts";
    public static final String POSTER_TX_PRODUCTS_CACHE = "posterTxProducts";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}
