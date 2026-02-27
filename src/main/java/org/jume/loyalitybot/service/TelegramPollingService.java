package org.jume.loyalitybot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jume.loyalitybot.bot.LoyaltyBot;
import org.jume.loyalitybot.config.TelegramBotConfig;
import org.jume.loyalitybot.dto.TelegramUpdate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class TelegramPollingService {

    private final TelegramBotConfig config;
    private final LoyaltyBot loyaltyBot;
    private final ObjectMapper objectMapper;

    private RestClient restClient;
    private ScheduledExecutorService executor;
    private long lastUpdateId = 0;

    @PostConstruct
    public void start() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + config.getToken())
                .build();

        // Delete webhook for local polling
        try {
            restClient.post()
                    .uri("/deleteWebhook")
                    .retrieve()
                    .body(String.class);
            log.info("Webhook deleted for local polling mode");
        } catch (Exception e) {
            log.warn("Failed to delete webhook: {}", e.getMessage());
        }

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(this::pollUpdates, 0, 1, TimeUnit.SECONDS);
        log.info("Telegram polling started for bot @{}", config.getUsername());
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    private void pollUpdates() {
        try {
            String response = restClient.get()
                    .uri("/getUpdates?offset={offset}&timeout=30", lastUpdateId + 1)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            if (root.path("ok").asBoolean()) {
                JsonNode result = root.path("result");
                for (JsonNode updateNode : result) {
                    try {
                        TelegramUpdate update = parseUpdate(updateNode);
                        lastUpdateId = update.getUpdateId();
                        loyaltyBot.processUpdate(update);
                    } catch (Exception e) {
                        log.error("Error processing update", e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error polling updates", e);
        }
    }

    private TelegramUpdate parseUpdate(JsonNode node) {
        TelegramUpdate update = new TelegramUpdate();
        update.setUpdateId(node.path("update_id").asLong());

        JsonNode message = node.path("message");
        if (!message.isMissingNode()) {
            TelegramUpdate.TelegramMessage msg = new TelegramUpdate.TelegramMessage();
            msg.setMessageId(message.path("message_id").asLong());
            msg.setText(message.path("text").asText(null));
            msg.setDate(message.path("date").asLong());

            JsonNode chat = message.path("chat");
            TelegramUpdate.TelegramChat chatObj = new TelegramUpdate.TelegramChat();
            chatObj.setId(chat.path("id").asLong());
            chatObj.setType(chat.path("type").asText());
            chatObj.setFirstName(chat.path("first_name").asText(null));
            chatObj.setLastName(chat.path("last_name").asText(null));
            chatObj.setUsername(chat.path("username").asText(null));
            msg.setChat(chatObj);

            JsonNode from = message.path("from");
            TelegramUpdate.TelegramUser userObj = new TelegramUpdate.TelegramUser();
            userObj.setId(from.path("id").asLong());
            userObj.setFirstName(from.path("first_name").asText(null));
            userObj.setLastName(from.path("last_name").asText(null));
            userObj.setUsername(from.path("username").asText(null));
            userObj.setIsBot(from.path("is_bot").asBoolean());
            userObj.setLanguageCode(from.path("language_code").asText(null));
            msg.setFrom(userObj);

            JsonNode contact = message.path("contact");
            if (!contact.isMissingNode()) {
                TelegramUpdate.TelegramContact contactObj = new TelegramUpdate.TelegramContact();
                contactObj.setPhoneNumber(contact.path("phone_number").asText());
                contactObj.setFirstName(contact.path("first_name").asText(null));
                contactObj.setLastName(contact.path("last_name").asText(null));
                contactObj.setUserId(contact.path("user_id").asLong());
                msg.setContact(contactObj);
            }

            update.setMessage(msg);
        }

        return update;
    }
}
