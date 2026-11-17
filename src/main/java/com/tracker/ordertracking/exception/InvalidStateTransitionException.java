package com.tracker.ordertracking.exception;

import com.tracker.ordertracking.entity.OrderStatus;

/**
 * Thrown when someone tries an illegal move in the state machine, e.g.
 * PLACED -> DELIVERED. Mapped to HTTP 409 CONFLICT.
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String orderRef, OrderStatus from, OrderStatus to) {
        super(String.format(
                "Illegal transition for order %s: %s -> %s. Allowed next states from %s are %s",
                orderRef, from, to, from, from.allowedNextStates()));
    }
}
