package com.tracker.ordertracking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Turns the app into a STOMP-over-WebSocket server.
 *
 * - Browsers connect once to  ws://localhost:8080/ws
 * - They then SUBSCRIBE to    /topic/orders/{orderRef}            (status changes)
 *   and to                    /topic/orders/{orderRef}/location   (GPS pings)
 * - The server pushes to those destinations with SimpMessagingTemplate.
 *
 * The in-memory "simple broker" is used on purpose: for 1,000 concurrent
 * sockets it costs nothing, and the cross-instance fan-out problem is already
 * solved by Redis Pub/Sub (every instance receives every event and pushes it
 * to whichever customers happen to be connected to it).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")   // demo only - lock this down in production
                .withSockJS();                   // automatic fallback for old browsers/proxies
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Destinations the server broadcasts on.
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());

        // Prefix for messages sent *from* the browser to @MessageMapping methods.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // GPS frames are tiny; small buffers keep memory flat at 1,000+ sockets.
        registration.setMessageSizeLimit(64 * 1024)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(20_000);
    }

    /**
     * Heartbeats need a scheduler. Without them, an idle socket sitting behind a
     * corporate proxy gets silently killed after ~60s and the customer's map
     * freezes with no error.
     */
    private org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler heartbeatScheduler() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
