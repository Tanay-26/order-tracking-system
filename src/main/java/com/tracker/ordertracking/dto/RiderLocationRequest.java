package com.tracker.ordertracking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of POST /api/v1/riders/{riderId}/location - sent by the rider's phone
 * every 3 seconds. Deliberately tiny: the smaller this payload, the cheaper it
 * is to handle thousands of them per second.
 */
public record RiderLocationRequest(

        @NotBlank(message = "orderRef is required so we know which customer to notify")
        String orderRef,

        @NotNull(message = "latitude is required")
        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "longitude is required")
        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        Double longitude,

        @DecimalMin(value = "0.0", message = "speedKmph cannot be negative")
        Double speedKmph,

        @DecimalMin(value = "0.0", message = "bearing must be between 0 and 360")
        @DecimalMax(value = "360.0", message = "bearing must be between 0 and 360")
        Double bearing
) {
}
