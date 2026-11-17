package com.tracker.ordertracking.dto;

import com.tracker.ordertracking.entity.Order;
import com.tracker.ordertracking.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What the REST API returns for an order. Entities are never returned directly:
 * that would leak the DB id, the JPA version column and lazy-loading proxies.
 */
public record OrderResponse(
        String orderRef,
        String customerId,
        String customerName,
        String restaurantName,
        String pickupAddress,
        String deliveryAddress,
        BigDecimal totalAmount,
        OrderStatus status,
        String statusDescription,
        int stage,
        int totalStages,
        int progressPercentage,
        String riderId,
        String riderName,
        String riderPhone,
        Instant createdAt,
        Instant updatedAt
) {

    /** Single place that knows how to turn an entity into an API response. */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderRef(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getRestaurantName(),
                order.getPickupAddress(),
                order.getDeliveryAddress(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getStatus().getDescription(),
                order.getStatus().getStage(),
                OrderStatus.TOTAL_STAGES,
                order.getStatus().progressPercentage(),
                order.getRiderId(),
                order.getRiderName(),
                order.getRiderPhone(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
