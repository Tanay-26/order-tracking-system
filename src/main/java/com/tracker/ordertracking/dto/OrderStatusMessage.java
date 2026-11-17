package com.tracker.ordertracking.dto;

import com.tracker.ordertracking.entity.OrderStatus;

import java.time.Instant;

/**
 * The payload pushed to /topic/orders/{orderRef} whenever an order changes state.
 * It carries enough context for the UI to redraw the whole progress bar without
 * making another REST call.
 */
public record OrderStatusMessage(
        String orderRef,
        OrderStatus previousStatus,
        OrderStatus currentStatus,
        String statusDescription,
        int stage,
        int totalStages,
        int progressPercentage,
        String note,
        String riderId,
        String eventId,
        Instant occurredAt
) {
}
