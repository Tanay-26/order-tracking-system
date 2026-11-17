package com.tracker.ordertracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Real-Time Order Tracking System.
 *
 * On startup Spring Boot wires together:
 *  - the REST layer (Tomcat on :8080)
 *  - the JPA layer (PostgreSQL connection pool)
 *  - the Redis layer (Lettuce connection pool, Stream consumer, Pub/Sub subscriber)
 *  - the WebSocket/STOMP broker
 */
@SpringBootApplication
public class OrderTrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderTrackingApplication.class, args);
    }
}
