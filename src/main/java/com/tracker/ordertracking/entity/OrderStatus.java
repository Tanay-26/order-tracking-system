package com.tracker.ordertracking.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The 6 happy-path stages of an order, plus the terminal CANCELLED state.
 *
 * This enum IS the state machine. Every transition attempt in the system is
 * validated against {@link #canTransitionTo(OrderStatus)}, which makes it
 * impossible for a buggy client (or a duplicated Redis event) to move an order
 * backwards, e.g. from DELIVERED back to PREPARING.
 */
public enum OrderStatus {

    PLACED(1, "Order placed"),
    CONFIRMED(2, "Restaurant confirmed the order"),
    PREPARING(3, "Food is being prepared"),
    PICKED_UP(4, "Rider picked up the order"),
    OUT_FOR_DELIVERY(5, "Rider is on the way"),
    DELIVERED(6, "Order delivered"),
    CANCELLED(-1, "Order cancelled");

    public static final int TOTAL_STAGES = 6;

    private final int stage;
    private final String description;

    /** Allowed next states for every state. Anything not listed here is rejected. */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(PLACED, EnumSet.of(CONFIRMED, CANCELLED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(PREPARING, CANCELLED));
        TRANSITIONS.put(PREPARING, EnumSet.of(PICKED_UP, CANCELLED));
        TRANSITIONS.put(PICKED_UP, EnumSet.of(OUT_FOR_DELIVERY, CANCELLED));
        TRANSITIONS.put(OUT_FOR_DELIVERY, EnumSet.of(DELIVERED));
        TRANSITIONS.put(DELIVERED, Collections.emptySet());
        TRANSITIONS.put(CANCELLED, Collections.emptySet());
    }

    OrderStatus(int stage, String description) {
        this.stage = stage;
        this.description = description;
    }

    public int getStage() {
        return stage;
    }

    public String getDescription() {
        return description;
    }

    /** True when the order can never change state again. */
    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** True when 'next' is a legal move from the current state. */
    public boolean canTransitionTo(OrderStatus next) {
        return next != null && TRANSITIONS.get(this).contains(next);
    }

    /** Used for building helpful 409 error messages. */
    public Set<OrderStatus> allowedNextStates() {
        return Collections.unmodifiableSet(TRANSITIONS.get(this));
    }

    /** 0-100 progress bar value shown on the customer's tracking screen. */
    public int progressPercentage() {
        if (this == CANCELLED) {
            return 0;
        }
        return (int) Math.round((stage * 100.0) / TOTAL_STAGES);
    }
}
