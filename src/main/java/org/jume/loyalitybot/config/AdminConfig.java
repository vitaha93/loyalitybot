package org.jume.loyalitybot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@ConfigurationProperties(prefix = "admin")
@Getter
@Setter
public class AdminConfig {

    private String telegramIds = "";

    public Set<Long> getAdminTelegramIds() {
        if (telegramIds == null || telegramIds.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(telegramIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    public boolean isAdmin(Long telegramId) {
        return getAdminTelegramIds().contains(telegramId);
    }
}
