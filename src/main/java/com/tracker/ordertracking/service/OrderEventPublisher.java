package com.tracker.ordertracking.service;

import com.tracker.ordertracking.entity.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends one record to the Redis Stream for every state change.
 *
 * This is the "write side" of the event-driven flow. It is deliberately dumb
 * and fast (a single XADD, roughly 0.2 ms) because it runs inside the HTTP
 * request thread. Everything expensive - audit inserts, fan-out - happens later
 * on the consumer side.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    public static final String NO_PREVIOUS_STATE = "NONE";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.redis.stream.key}")
    private String streamKey;

    @Value("${app.redis.stream.max-length}")
    private long maxStreamLength;

    /**
     * @return the Redis record id (e.g. "1739270400123-0"), which we reuse as the
     *         idempotency key for the audit row.
     */
    public String publishStatusChange(String orderRef,
                                      OrderStatus fromStatus,
                                      OrderStatus toStatus,
                                      String note,
                                      String riderId) {

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("orderRef", orderRef);
        payload.put("fromStatus", fromStatus == null ? NO_PREVIOUS_STATE : fromStatus.name());
        payload.put("toStatus", toStatus.name());
        payload.put("note", note == null ? "" : note);
        payload.put("riderId", riderId == null ? "" : riderId);
        payload.put("occurredAt", Instant.now().toString());

        RecordId recordId = redisTemplate.opsForStream()
                .add(StreamRecords.newRecord().in(streamKey).ofMap(payload));

        // Keep the log bounded. "approximate = true" lets Redis trim on whole
        // node boundaries, which is O(1) instead of O(n).
        redisTemplate.opsForStream().trim(streamKey, maxStreamLength, true);

        String id = recordId == null ? "unknown" : recordId.getValue();
        log.debug("XADD {} -> {} ({} -> {})", streamKey, id, payload.get("fromStatus"), toStatus);
        return id;
    }
}
