package org.jume.loyalitybot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.IOException;
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
    @JsonDeserialize(using = TransactionDataDeserializer.class)
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

    /**
     * Custom deserializer to handle 'data' field that can be either:
     * - a JSON object (TransactionData)
     * - a JSON string containing JSON
     */
    public static class TransactionDataDeserializer extends JsonDeserializer<TransactionData> {
        private static final ObjectMapper mapper = new ObjectMapper();

        @Override
        public TransactionData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            if (node == null || node.isNull()) {
                return null;
            }

            // If it's a string, try to parse it as JSON
            if (node.isTextual()) {
                String jsonString = node.asText();
                try {
                    JsonNode innerNode = mapper.readTree(jsonString);
                    return mapper.treeToValue(innerNode, TransactionData.class);
                } catch (Exception e) {
                    // If parsing fails, return null
                    return null;
                }
            }

            // If it's already an object, deserialize directly
            if (node.isObject()) {
                return mapper.treeToValue(node, TransactionData.class);
            }

            return null;
        }
    }
}
