package org.jume.loyalitybot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "loyalty")
@Getter
@Setter
public class LoyaltyConfig {

    private BigDecimal welcomeBonus = BigDecimal.valueOf(50);

    private Integer bonusPercent = 5;

    private String currencySymbol = "грн";
}
