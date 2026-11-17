package com.tracker.ordertracking.exception;

/** Thrown when an orderRef does not exist. Mapped to HTTP 404 by the global handler. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderRef) {
        super("Order not found: " + orderRef);
    }
}
