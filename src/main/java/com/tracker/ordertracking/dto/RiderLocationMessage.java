package com.tracker.ordertracking.dto;

import java.time.Instant;

/**
 * What actually gets stored in Redis and pushed down the WebSocket for a GPS ping.
 * Kept flat and primitive so Jackson serialisation costs almost nothing.
 */
public record RiderLocationMessage(
        String riderId,
        String orderRef,
        Double latitude,
        Double longitude,
        Double speedKmph,
        Double bearing,
        Instant recordedAt
) {
}
