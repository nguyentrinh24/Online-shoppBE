package com.project.shopapp.dtos.coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CouponConditionDTO {
    @NotBlank(message = "Attribute is required")
    private String attribute;

    @NotBlank(message = "Operator is required")
    private String operator;

    @NotBlank(message = "Value is required")
    private String value;

    @NotNull(message = "Discount amount is required")
    @Positive(message = "Discount amount must be positive")
    private BigDecimal discountAmount;
} 