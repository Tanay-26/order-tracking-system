package com.tracker.ordertracking.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.ordertracking.dto.OrderStatusMessage;
import com.tracker.ordertracking.entity.OrderEvent;
import com.tracker.ordertracking.entity.OrderStatus;
import com.tracker.ordertracking.repository.OrderEventRepository;
import com.tracker.ordertracking.service.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The "read side" of the event-driven flow. One instance of this runs in every
 * application node and is fed by the consumer group.
 *
 * For each record it:
 *   1. writes the immutable audit row into PostgreSQL (idempotently),
 *   2. builds the customer-facing message,
 *   3. publishes it on Redis Pub/Sub so every node can push it over WebSocket,
 *   4. acknowledges the record (XACK) so it leaves the Pending Entries List.
 *
 * If step 1 or 2 throws, the record is copied to a dead-letter stream and then
 * acknowledged, so one poisoned message can never block the whole pipeline.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final OrderEventRepository orderEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.stream.key}")
    private String streamKey;

    @Value("${app.redis.stream.group}")
    private String consumerGroup;

    @Value("${app.redis.stream.dlq-key}")
    private String deadLetterKey;

    @Value("${app.redis.channel.order-status}")
    private String orderStatusChannel;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String recordId = record.getId().getValue();
        Map<String, String> body = record.getValue();

        // The bootstrap record created when the stream did not exist yet.
        if (body.containsKey("bootstrap")) {
            acknowledge(recordId);
            return;
        }

        try {
            String orderRef = body.get("orderRef");
            String rawFrom = body.getOrDefault("fromStatus", OrderEventPublisher.NO_PREVIOUS_STATE);
            OrderStatus fromStatus = OrderEventPublisher.NO_PREVIOUS_STATE.equals(rawFrom) || rawFrom.isBlank()
                    ? null
                    : OrderStatus.valueOf(rawFrom);
            OrderStatus toStatus = OrderStatus.valueOf(body.get("toStatus"));
            String note = emptyToNull(body.get("note"));
            String riderId = emptyToNull(body.get("riderId"));
            Instant occurredAt = parseInstant(body.get("occurredAt"));

            // --- 1. Durable audit trail, guarded against re-delivery ---------
            if (!orderEventRepository.existsByEventId(recordId)) {
                orderEventRepository.save(OrderEvent.builder()
                        .eventId(recordId)
                        .orderRef(orderRef)
                        .fromStatus(fromStatus)
                        .toStatus(toStatus)
                        .note(note)
                        .riderId(riderId)
                        .occurredAt(occurredAt)
                        .build());
            } else {
                log.info("Event {} already persisted - skipping duplicate", recordId);
            }

            // --- 2. Build the message the customer's screen will render ------
            OrderStatusMessage message = new OrderStatusMessage(
                    orderRef,
                    fromStatus,
                    toStatus,
                    toStatus.getDescription(),
                    toStatus.getStage(),
                    OrderStatus.TOTAL_STAGES,
                    toStatus.progressPercentage(),
                    note,
                    riderId,
                    recordId,
                    occurredAt);

            // --- 3. Fan out to every application instance --------------------
            redisTemplate.convertAndSend(orderStatusChannel, objectMapper.writeValueAsString(message));

            log.info("Processed event {} for order {} -> {}", recordId, orderRef, toStatus);

        } catch (Exception e) {
            log.error("Failed to process stream record {} - moving to DLQ. Reason: {}", recordId, e.getMessage(), e);
            moveToDeadLetter(record, e);
        } finally {
            // --- 4. Always XACK ---------------------------------------------
            acknowledge(recordId);
        }
    }

    private void acknowledge(String recordId) {
        try {
            redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, recordId);
        } catch (Exception e) {
            log.error("XACK failed for record {}: {}", recordId, e.getMessage());
        }
    }

    private void moveToDeadLetter(MapRecord<String, String, String> record, Exception cause) {
        try {
            Map<String, String> dlqPayload = new HashMap<>(record.getValue());
            dlqPayload.put("failedRecordId", record.getId().getValue());
            dlqPayload.put("failureReason", String.valueOf(cause.getMessage()));
            dlqPayload.put("failedAt", Instant.now().toString());
            redisTemplate.opsForStream().add(StreamRecords.newRecord().in(deadLetterKey).ofMap(dlqPayload));
        } catch (Exception e) {
            log.error("Could not write record to DLQ: {}", e.getMessage());
        }
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
