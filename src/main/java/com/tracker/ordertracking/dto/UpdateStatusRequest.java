package com.tracker.ordertracking.dto;

import com.tracker.ordertracking.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of PATCH /api/v1/orders/{orderRef}/status */
public record UpdateStatusRequest(

        @NotNull(message = "status is required (PLACED, CONFIRMED, PREPARING, PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, CANCELLED)")
        OrderStatus status,

        @Size(max = 400)
        String note
) {
}
