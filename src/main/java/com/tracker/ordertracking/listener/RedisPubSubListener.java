package com.tracker.ordertracking.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.ordertracking.dto.OrderStatusMessage;
import com.tracker.ordertracking.dto.RiderLocationMessage;
import com.tracker.ordertracking.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * The bridge between Redis Pub/Sub and the browsers.
 *
 * Every application instance runs this listener and receives EVERY message.
 * It then hands the message to NotificationService, which pushes it to whatever
 * WebSocket sessions happen to live on this particular instance. Sessions
 * connected elsewhere are served by that other instance's copy of this class.
 *
 * That is the whole trick behind horizontal scaling of a stateful push system.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisPubSubListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Value("${app.redis.channel.order-status}")
    private String orderStatusChannel;

    @Value("${app.redis.channel.rider-location}")
    private String riderLocationChannel;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            if (orderStatusChannel.equals(channel)) {
                notificationService.pushStatusUpdate(objectMapper.readValue(payload, OrderStatusMessage.class));
            } else if (riderLocationChannel.equals(channel)) {
                notificationService.pushRiderLocation(objectMapper.readValue(payload, RiderLocationMessage.class));
            } else {
                log.warn("Received a message on an unexpected channel: {}", channel);
            }
        } catch (Exception e) {
            // A single malformed payload must not kill the subscriber thread.
            log.error("Could not handle Pub/Sub message on channel {}: {}", channel, e.getMessage(), e);
        }
    }
}
