package com.tracker.ordertracking.service;

import com.tracker.ordertracking.dto.OrderStatusMessage;
import com.tracker.ordertracking.dto.RiderLocationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * The only class in the application allowed to touch the WebSocket broker.
 *
 * Destination contract shared with the frontend:
 *   /topic/orders/{orderRef}            -> OrderStatusMessage
 *   /topic/orders/{orderRef}/location   -> RiderLocationMessage
 *   /topic/riders/{riderId}/location    -> RiderLocationMessage (ops dashboard)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void pushStatusUpdate(OrderStatusMessage message) {
        String destination = "/topic/orders/" + message.orderRef();
        messagingTemplate.convertAndSend(destination, message);
        log.debug("WS push {} -> {}", destination, message.currentStatus());
    }

    public void pushRiderLocation(RiderLocationMessage message) {
        // Customers watching this one order.
        messagingTemplate.convertAndSend("/topic/orders/" + message.orderRef() + "/location", message);
        // Operations dashboard watching a specific rider.
        messagingTemplate.convertAndSend("/topic/riders/" + message.riderId() + "/location", message);
        log.trace("WS push location for order {} rider {}", message.orderRef(), message.riderId());
    }
}
