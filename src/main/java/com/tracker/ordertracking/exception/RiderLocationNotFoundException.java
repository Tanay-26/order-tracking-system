package com.tracker.ordertracking.exception;

/**
 * Thrown when a rider has no live location in Redis - either they never pinged,
 * or their last ping is older than the TTL and Redis has already expired it.
 */
public class RiderLocationNotFoundException extends RuntimeException {

    public RiderLocationNotFoundException(String riderId) {
        super("No live location for rider " + riderId + " (never pinged, or last ping expired)");
    }
}
