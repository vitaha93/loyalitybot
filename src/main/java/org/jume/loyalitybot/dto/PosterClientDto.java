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
public class PosterClientDto {

    @JsonProperty("client_id")
    private Long clientId;

    @JsonProperty("firstname")
    private String firstName;

    @JsonProperty("lastname")
    private String lastName;

    private String phone;

    @JsonProperty("phone_number")
    private String phoneNumber;

    private String email;

    private String birthday;

    @JsonProperty("client_sex")
    private Integer clientSex;

    @JsonProperty("bonus")
    private BigDecimal bonusKopecks;

    /**
     * Returns bonus in hryvnia (divided by 100)
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getBonusInHryvnia() {
        if (bonusKopecks == null) return BigDecimal.ZERO;
        return bonusKopecks.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    @JsonProperty("total_payed_sum")
    private BigDecimal totalPayedSum;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getTotalPayedSumInHryvnia() {
        if (totalPayedSum == null) return BigDecimal.ZERO;
        return totalPayedSum.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    @JsonProperty("date_activale")
    private String dateActivate;

    @JsonProperty("discount_per")
    private Integer discountPercent;

    private String card;

    @JsonProperty("card_number")
    private String cardNumber;

    @JsonProperty("client_groups_id")
    private Long clientGroupsId;

    public String getNormalizedPhone() {
        String p = phone != null ? phone : phoneNumber;
        if (p == null) return null;
        return p.replaceAll("[^0-9+]", "");
    }
}
