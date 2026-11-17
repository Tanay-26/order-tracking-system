package com.tracker.ordertracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of PATCH /api/v1/orders/{orderRef}/rider */
public record AssignRiderRequest(

        @NotBlank(message = "riderId is required")
        @Size(max = 64)
        String riderId,

        @Size(max = 120)
        String riderName,

        @Size(max = 24)
        String riderPhone
) {
}
