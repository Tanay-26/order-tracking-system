package com.tracker.ordertracking.dto;

import com.tracker.ordertracking.entity.OrderEvent;
import com.tracker.ordertracking.entity.OrderStatus;

import java.time.Instant;

/** One row of the "order journey" timeline returned by GET /orders/{ref}/events */
public record OrderEventResponse(
        String eventId,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String note,
        String riderId,
        Instant occurredAt
) {

    public static OrderEventResponse from(OrderEvent event) {
        return new OrderEventResponse(
                event.getEventId(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getNote(),
                event.getRiderId(),
                event.getOccurredAt()
        );
    }
}
