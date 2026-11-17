package com.tracker.ordertracking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The order row in PostgreSQL. This is the system of record: if Redis is wiped
 * clean, every order and its current state can still be rebuilt from here.
 */
@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_order_ref", columnList = "order_ref", unique = true),
                @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
                @Index(name = "idx_orders_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public, human-readable id such as ORD-4F9A2C81. Never expose the DB id. */
    @Column(name = "order_ref", nullable = false, unique = true, length = 40)
    private String orderRef;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "restaurant_name", nullable = false, length = 160)
    private String restaurantName;

    @Column(name = "pickup_address", length = 400)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false, length = 400)
    private String deliveryAddress;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "rider_id", length = 64)
    private String riderId;

    @Column(name = "rider_name", length = 120)
    private String riderName;

    @Column(name = "rider_phone", length = 24)
    private String riderPhone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking. If two threads (say the restaurant app and an admin
     * panel) update the same order at the same time, the second commit fails
     * with an ObjectOptimisticLockingFailureException instead of silently
     * overwriting the first one.
     */
    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = OrderStatus.PLACED;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
