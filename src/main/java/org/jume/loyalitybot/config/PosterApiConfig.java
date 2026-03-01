package org.jume.loyalitybot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;

import jakarta.validation.constraints.NotBlank;

@Configuration
@ConfigurationProperties(prefix = "poster.api")
@Validated
@Getter
@Setter
public class PosterApiConfig {

    @NotBlank(message = "Poster API base URL is required")
    private String baseUrl = "https://joinposter.com/api";

    @NotBlank(message = "Poster API token is required")
    private String token;

    private String account;

    private Long defaultClientGroupId;

    @Bean
    public RestClient posterRestClient() {
        String url = account != null && !account.isBlank()
                ? "https://" + account + ".joinposter.com/api"
                : baseUrl;
        return RestClient.builder()
                .baseUrl(url)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
