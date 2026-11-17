package com.tracker.ordertracking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of POST /api/v1/orders.
 * Every field is validated before a single line of business logic runs.
 */
public record CreateOrderRequest(

        @NotBlank(message = "customerId is required")
        @Size(max = 64)
        String customerId,

        @Size(max = 120)
        String customerName,

        @NotBlank(message = "restaurantName is required")
        @Size(max = 160)
        String restaurantName,

        @Size(max = 400)
        String pickupAddress,

        @NotBlank(message = "deliveryAddress is required")
        @Size(max = 400)
        String deliveryAddress,

        @NotNull(message = "totalAmount is required")
        @DecimalMin(value = "1.0", message = "totalAmount must be at least 1.0")
        BigDecimal totalAmount
) {
}
