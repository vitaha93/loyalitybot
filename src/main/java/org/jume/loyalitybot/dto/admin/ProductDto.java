package org.jume.loyalitybot.dto.admin;

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
public class ProductDto {

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("menu_category_id")
    private Long menuCategoryId;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("price")
    private BigDecimal priceKopecks;

    @JsonProperty("photo")
    private String photo;

    @JsonProperty("photo_origin")
    private String photoOrigin;

    public BigDecimal getPriceInHryvnia() {
        if (priceKopecks == null) return BigDecimal.ZERO;
        return priceKopecks.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }
}
