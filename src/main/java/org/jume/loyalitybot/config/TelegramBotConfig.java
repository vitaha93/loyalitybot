package org.jume.loyalitybot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
@Validated
@Getter
@Setter
public class TelegramBotConfig {

    @NotBlank(message = "Telegram bot token is required")
    private String token;

    @NotBlank(message = "Telegram bot username is required")
    private String username;

    private String webhookPath = "/api/webhook/telegram";
}
