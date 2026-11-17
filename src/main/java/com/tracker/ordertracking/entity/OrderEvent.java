package com.tracker.ordertracking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable audit trail: one row per state transition.
 *
 * Rows are written by the Redis Stream consumer (not by the REST thread), so
 * the customer's HTTP response is never slowed down by audit writes.
 * The unique index on event_id gives us idempotency: if Redis re-delivers the
 * same stream record after a crash, the insert is skipped instead of duplicated.
 */
@Entity
@Table(
        name = "order_events",
        indexes = {
                @Index(name = "idx_events_order_ref", columnList = "order_ref"),
                @Index(name = "idx_events_event_id", columnList = "event_id", unique = true)
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The Redis Stream record id, e.g. 1730712345678-0. Used for idempotency. */
    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "order_ref", nullable = false, length = 40)
    private String orderRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private OrderStatus toStatus;

    @Column(name = "note", length = 400)
    private String note;

    @Column(name = "rider_id", length = 64)
    private String riderId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
