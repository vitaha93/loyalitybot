package org.jume.loyalitybot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.config.TelegramBotConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TelegramApiClient {

    private static final String BASE_URL = "https://api.telegram.org/bot";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TelegramApiClient(TelegramBotConfig config, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL + config.getToken())
                .build();
        this.objectMapper = objectMapper;
    }

    public void sendMessage(Long chatId, String text, String parseMode, Object replyMarkup) {
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("chat_id", chatId);
            body.put("text", text);
            if (parseMode != null) {
                body.put("parse_mode", parseMode);
            }
            if (replyMarkup != null) {
                body.put("reply_markup", replyMarkup);
            }

            String response = restClient.post()
                    .uri("/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode result = objectMapper.readTree(response);
            if (!result.path("ok").asBoolean()) {
                log.error("Failed to send message: {}", result);
            }
        } catch (Exception e) {
            log.error("Error sending message to chat {}", chatId, e);
            throw new RuntimeException("Failed to send Telegram message", e);
        }
    }

    public void sendPhoto(Long chatId, byte[] imageData, String caption, String parseMode) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", chatId.toString());
            body.add("photo", new ByteArrayResource(imageData) {
                @Override
                public String getFilename() {
                    return "card.png";
                }
            });
            if (caption != null) {
                body.add("caption", caption);
            }
            if (parseMode != null) {
                body.add("parse_mode", parseMode);
            }

            String response = restClient.post()
                    .uri("/sendPhoto")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode result = objectMapper.readTree(response);
            if (!result.path("ok").asBoolean()) {
                log.error("Failed to send photo: {}", result);
            }
        } catch (Exception e) {
            log.error("Error sending photo to chat {}", chatId, e);
            throw new RuntimeException("Failed to send Telegram photo", e);
        }
    }

    public Map<String, Object> createContactKeyboard() {
        return Map.of(
                "keyboard", List.of(
                        List.of(Map.of(
                                "text", "Поділитися номером телефону",
                                "request_contact", true
                        ))
                ),
                "resize_keyboard", true,
                "one_time_keyboard", true
        );
    }

    public Map<String, Object> removeKeyboard() {
        return Map.of("remove_keyboard", true);
    }

    public Map<String, Object> createMainMenuKeyboard() {
        return Map.of(
                "keyboard", List.of(
                        List.of(
                                Map.of("text", "💰 Баланс"),
                                Map.of("text", "🎫 Картка")
                        ),
                        List.of(
                                Map.of("text", "❓ Допомога")
                        )
                ),
                "resize_keyboard", true,
                "is_persistent", true
        );
    }
}
