package org.jume.loyalitybot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PosterWebhookPayload {

    private String account;

    @JsonProperty("object")
    private String objectType;

    @JsonProperty("object_id")
    private String objectId;

    private String action;

    private String time;

    private String verify;

    @JsonProperty("data")
    private TransactionData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionData {

        @JsonProperty("transaction_id")
        private String transactionId;

        @JsonProperty("client_id")
        private Long clientId;

        @JsonProperty("sum")
        private BigDecimal sum;

        @JsonProperty("bonus")
        private BigDecimal bonus;

        @JsonProperty("bonus_added")
        private BigDecimal bonusAdded;

        @JsonProperty("bonus_used")
        private BigDecimal bonusUsed;

        @JsonProperty("date_close")
        private String dateClose;

        @JsonProperty("spot_id")
        private Long spotId;
    }
}
