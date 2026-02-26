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
    private BigDecimal bonus;

    @JsonProperty("total_payed_sum")
    private BigDecimal totalPayedSum;

    @JsonProperty("date_activale")
    private String dateActivate;

    @JsonProperty("discount_per")
    private Integer discountPercent;

    private String card;

    @JsonProperty("client_groups_id")
    private Long clientGroupsId;

    public String getNormalizedPhone() {
        String p = phone != null ? phone : phoneNumber;
        if (p == null) return null;
        return p.replaceAll("[^0-9+]", "");
    }
}
