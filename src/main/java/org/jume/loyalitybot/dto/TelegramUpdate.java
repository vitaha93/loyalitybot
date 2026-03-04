package org.jume.loyalitybot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdate {

    @JsonProperty("update_id")
    private Long updateId;

    private TelegramMessage message;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramMessage {
        @JsonProperty("message_id")
        private Long messageId;

        private TelegramUser from;

        private TelegramChat chat;

        private String text;

        private TelegramContact contact;

        private Long date;

        @JsonProperty("reply_to_message")
        private TelegramMessage replyToMessage;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramUser {
        private Long id;

        @JsonProperty("is_bot")
        private Boolean isBot;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        private String username;

        @JsonProperty("language_code")
        private String languageCode;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramChat {
        private Long id;

        private String type;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        private String username;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramContact {
        @JsonProperty("phone_number")
        private String phoneNumber;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        @JsonProperty("user_id")
        private Long userId;
    }

    public boolean hasMessage() {
        return message != null;
    }

    public boolean hasText() {
        return message != null && message.getText() != null;
    }

    public boolean hasContact() {
        return message != null && message.getContact() != null;
    }

    public boolean hasReplyToMessage() {
        return message != null && message.getReplyToMessage() != null;
    }
}
