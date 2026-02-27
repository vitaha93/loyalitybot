package org.jume.loyalitybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.AdminConfig;
import org.jume.loyalitybot.config.TelegramBotConfig;
import org.jume.loyalitybot.dto.admin.TelegramLoginData;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    private final TelegramBotConfig telegramConfig;
    private final AdminConfig adminConfig;

    private static final long AUTH_TIMEOUT_SECONDS = 86400; // 24 hours

    public boolean verifyTelegramLogin(TelegramLoginData loginData) {
        if (loginData == null || loginData.getId() == null || loginData.getHash() == null) {
            log.warn("Invalid login data: missing required fields");
            return false;
        }

        // Check if auth is not too old
        long currentTime = Instant.now().getEpochSecond();
        if (currentTime - loginData.getAuthDate() > AUTH_TIMEOUT_SECONDS) {
            log.warn("Auth data expired for user {}", loginData.getId());
            return false;
        }

        // Verify the hash
        String dataCheckString = buildDataCheckString(loginData);
        String calculatedHash = calculateHash(dataCheckString);

        if (!calculatedHash.equals(loginData.getHash())) {
            log.warn("Invalid hash for user {}. Expected: {}, Got: {}",
                loginData.getId(), calculatedHash, loginData.getHash());
            return false;
        }

        log.info("Telegram login verified for user {}", loginData.getId());
        return true;
    }

    public boolean isAdmin(Long telegramId) {
        return adminConfig.isAdmin(telegramId);
    }

    private String buildDataCheckString(TelegramLoginData data) {
        Map<String, String> params = new TreeMap<>();

        if (data.getId() != null) params.put("id", String.valueOf(data.getId()));
        if (data.getFirstName() != null) params.put("first_name", data.getFirstName());
        if (data.getLastName() != null) params.put("last_name", data.getLastName());
        if (data.getUsername() != null) params.put("username", data.getUsername());
        if (data.getPhotoUrl() != null) params.put("photo_url", data.getPhotoUrl());
        if (data.getAuthDate() != null) params.put("auth_date", String.valueOf(data.getAuthDate()));

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private String calculateHash(String dataCheckString) {
        try {
            // SHA256 of bot token
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] secretKey = digest.digest(telegramConfig.getToken().getBytes(StandardCharsets.UTF_8));

            // HMAC-SHA256 of data check string
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] hashBytes = hmac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Error calculating hash", e);
            return "";
        }
    }
}
