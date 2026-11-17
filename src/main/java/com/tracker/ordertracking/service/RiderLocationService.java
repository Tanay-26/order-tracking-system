package com.tracker.ordertracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.ordertracking.dto.RiderLocationMessage;
import com.tracker.ordertracking.dto.RiderLocationRequest;
import com.tracker.ordertracking.exception.RiderLocationNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The hot path of the whole system: ~1,000 riders x 1 ping every 3 seconds.
 *
 * Design rule: a GPS ping NEVER touches PostgreSQL.
 * A location is only interesting for a few seconds, so it lives in Redis with a
 * TTL and simply evaporates when it goes stale. That keeps the write cost at a
 * couple of in-memory operations instead of a disk-backed INSERT plus index
 * maintenance plus WAL flush.
 *
 * Redis keys used:
 *   rider:location:{riderId}  STRING, TTL 30s  -> latest position
 *   rider:trail:{riderId}     LIST, capped 100 -> breadcrumb trail for the map
 *   rider:order:{riderId}     STRING, TTL 6h   -> which order this rider carries
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiderLocationService {

    private static final String LOCATION_KEY_PREFIX = "rider:location:";
    private static final String TRAIL_KEY_PREFIX = "rider:trail:";
    private static final String RIDER_ORDER_KEY_PREFIX = "rider:order:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rider.location-ttl-seconds}")
    private long locationTtlSeconds;

    @Value("${app.rider.trail-size}")
    private long trailSize;

    @Value("${app.redis.channel.rider-location}")
    private String riderLocationChannel;

    /**
     * Called ~333 times/second at 1,000 active riders. Everything in here is
     * O(1) against Redis and there is no database round trip at all.
     */
    public RiderLocationMessage updateLocation(String riderId, RiderLocationRequest request) {
        long startNanos = System.nanoTime();

        String orderRef = resolveOrderRef(riderId, request.orderRef());

        RiderLocationMessage message = new RiderLocationMessage(
                riderId,
                orderRef,
                request.latitude(),
                request.longitude(),
                request.speedKmph() == null ? 0.0 : request.speedKmph(),
                request.bearing() == null ? 0.0 : request.bearing(),
                Instant.now());

        try {
            String json = objectMapper.writeValueAsString(message);

            // 1. Latest position, auto-expiring. If the rider's phone dies, the
            //    key disappears on its own - no cleanup job, no stale pin on the map.
            redisTemplate.opsForValue().set(
                    LOCATION_KEY_PREFIX + riderId, json, Duration.ofSeconds(locationTtlSeconds));

            // 2. Capped breadcrumb trail so the UI can draw the path travelled.
            String trailKey = TRAIL_KEY_PREFIX + riderId;
            redisTemplate.opsForList().leftPush(trailKey, json);
            redisTemplate.opsForList().trim(trailKey, 0, trailSize - 1);
            redisTemplate.expire(trailKey, Duration.ofHours(2));

            // 3. Fan out to every app instance, which pushes it to the customer's
            //    open WebSocket. Fire-and-forget on purpose: a lost ping is
            //    irrelevant because a fresher one arrives in 3 seconds.
            redisTemplate.convertAndSend(riderLocationChannel, json);

        } catch (Exception e) {
            log.error("Failed to process location ping for rider {}: {}", riderId, e.getMessage());
            throw new IllegalStateException("Could not process the location ping", e);
        }

        long elapsedMicros = (System.nanoTime() - startNanos) / 1000;
        log.debug("Location ping rider={} order={} handled in {} us", riderId, orderRef, elapsedMicros);
        return message;
    }

    /** Latest known position, or 404 if the TTL already expired it. */
    public RiderLocationMessage getLatestLocation(String riderId) {
        String json = redisTemplate.opsForValue().get(LOCATION_KEY_PREFIX + riderId);
        if (json == null) {
            throw new RiderLocationNotFoundException(riderId);
        }
        try {
            return objectMapper.readValue(json, RiderLocationMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupted location payload for rider " + riderId, e);
        }
    }

    /** The last N positions, newest first - used to draw the route line on the map. */
    public List<RiderLocationMessage> getTrail(String riderId) {
        List<String> raw = redisTemplate.opsForList().range(TRAIL_KEY_PREFIX + riderId, 0, trailSize - 1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return raw.stream()
                .map(this::parseQuietly)
                .filter(Objects::nonNull)
                .toList();
    }

    private RiderLocationMessage parseQuietly(String json) {
        try {
            return objectMapper.readValue(json, RiderLocationMessage.class);
        } catch (Exception e) {
            log.warn("Skipping unparsable trail entry: {}", e.getMessage());
            return null;
        }
    }

    /**
     * The rider app normally sends the orderRef. If it does not, we fall back to
     * the rider -> order mapping written when the rider was assigned.
     */
    private String resolveOrderRef(String riderId, String requestedOrderRef) {
        if (requestedOrderRef != null && !requestedOrderRef.isBlank()) {
            return requestedOrderRef;
        }
        String mapped = redisTemplate.opsForValue().get(RIDER_ORDER_KEY_PREFIX + riderId);
        if (mapped == null) {
            throw new IllegalArgumentException(
                    "No orderRef supplied and rider " + riderId + " is not assigned to any order");
        }
        return mapped;
    }
}
