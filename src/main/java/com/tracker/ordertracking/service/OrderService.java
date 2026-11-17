package com.tracker.ordertracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.ordertracking.dto.AssignRiderRequest;
import com.tracker.ordertracking.dto.CreateOrderRequest;
import com.tracker.ordertracking.dto.OrderEventResponse;
import com.tracker.ordertracking.dto.OrderResponse;
import com.tracker.ordertracking.dto.UpdateStatusRequest;
import com.tracker.ordertracking.entity.Order;
import com.tracker.ordertracking.entity.OrderStatus;
import com.tracker.ordertracking.exception.InvalidStateTransitionException;
import com.tracker.ordertracking.exception.OrderNotFoundException;
import com.tracker.ordertracking.repository.OrderEventRepository;
import com.tracker.ordertracking.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All order business logic lives here. Controllers stay thin, the stream
 * listener stays dumb, and every rule about how an order may change is in one
 * readable place.
 *
 * Write path  : validate -> PostgreSQL -> refresh Redis cache -> XADD event -> return
 * Read path   : Redis cache (hit) OR PostgreSQL (miss, then populate cache)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final String ORDER_CACHE_PREFIX = "order:";

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.order-ttl-seconds}")
    private long orderCacheTtlSeconds;

    // ------------------------------------------------------------------
    //  CREATE
    // ------------------------------------------------------------------

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = Order.builder()
                .orderRef(generateOrderRef())
                .customerId(request.customerId())
                .customerName(request.customerName())
                .restaurantName(request.restaurantName())
                .pickupAddress(request.pickupAddress())
                .deliveryAddress(request.deliveryAddress())
                .totalAmount(request.totalAmount())
                .status(OrderStatus.PLACED)
                .build();

        Order saved = orderRepository.save(order);
        OrderResponse response = OrderResponse.from(saved);

        cacheOrder(response);
        eventPublisher.publishStatusChange(
                saved.getOrderRef(), null, OrderStatus.PLACED, "Order created", null);

        log.info("Created order {} for customer {}", saved.getOrderRef(), saved.getCustomerId());
        return response;
    }

    // ------------------------------------------------------------------
    //  STATE TRANSITION  (the heart of the system)
    // ------------------------------------------------------------------

    @Transactional
    public OrderResponse updateStatus(String orderRef, UpdateStatusRequest request) {
        Order order = orderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new OrderNotFoundException(orderRef));

        OrderStatus current = order.getStatus();
        OrderStatus target = request.status();

        // Idempotency: replaying the same transition is a no-op, not an error.
        // Mobile clients retry aggressively on flaky networks; this stops a
        // duplicate POST from creating a duplicate timeline entry.
        if (current == target) {
            log.info("Order {} is already {} - ignoring duplicate transition", orderRef, target);
            return OrderResponse.from(order);
        }

        if (!current.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(orderRef, current, target);
        }

        order.setStatus(target);
        Order saved = orderRepository.save(order);
        OrderResponse response = OrderResponse.from(saved);

        // 1. Refresh the cache so the very next GET is already correct.
        cacheOrder(response);

        // 2. Append to the durable event log. The consumer will write the audit
        //    row and fan the update out to every connected customer.
        eventPublisher.publishStatusChange(
                orderRef, current, target, request.note(), saved.getRiderId());

        log.info("Order {} transitioned {} -> {}", orderRef, current, target);
        return response;
    }

    // ------------------------------------------------------------------
    //  RIDER ASSIGNMENT
    // ------------------------------------------------------------------

    @Transactional
    public OrderResponse assignRider(String orderRef, AssignRiderRequest request) {
        Order order = orderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new OrderNotFoundException(orderRef));

        if (order.getStatus().isTerminal()) {
            throw new IllegalArgumentException(
                    "Cannot assign a rider to order " + orderRef + " because it is already " + order.getStatus());
        }

        order.setRiderId(request.riderId());
        order.setRiderName(request.riderName());
        order.setRiderPhone(request.riderPhone());
        Order saved = orderRepository.save(order);

        OrderResponse response = OrderResponse.from(saved);
        cacheOrder(response);

        // Map rider -> order in Redis so a GPS ping can be routed even if the
        // rider app forgets to send the orderRef.
        redisTemplate.opsForValue().set(
                "rider:order:" + request.riderId(), orderRef, Duration.ofHours(6));

        log.info("Assigned rider {} to order {}", request.riderId(), orderRef);
        return response;
    }

    // ------------------------------------------------------------------
    //  READS
    // ------------------------------------------------------------------

    /** Cache-aside read: try Redis first, fall back to PostgreSQL, then warm the cache. */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderRef) {
        Optional<OrderResponse> cached = readFromCache(orderRef);
        if (cached.isPresent()) {
            log.debug("Cache HIT for {}", orderRef);
            return cached.get();
        }

        log.debug("Cache MISS for {} - reading PostgreSQL", orderRef);
        Order order = orderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new OrderNotFoundException(orderRef));

        OrderResponse response = OrderResponse.from(order);
        cacheOrder(response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForCustomer(String customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** The full journey of the order, straight from the PostgreSQL audit table. */
    @Transactional(readOnly = true)
    public List<OrderEventResponse> getOrderTimeline(String orderRef) {
        if (!orderRepository.existsByOrderRef(orderRef)) {
            throw new OrderNotFoundException(orderRef);
        }
        return orderEventRepository.findByOrderRefOrderByOccurredAtAsc(orderRef)
                .stream()
                .map(OrderEventResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------
    //  HELPERS
    // ------------------------------------------------------------------

    private String generateOrderRef() {
        String ref;
        do {
            ref = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (orderRepository.existsByOrderRef(ref));   // astronomically unlikely, still checked
        return ref;
    }

    private void cacheOrder(OrderResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    ORDER_CACHE_PREFIX + response.orderRef(),
                    objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(orderCacheTtlSeconds));
        } catch (Exception e) {
            // A cache failure must never break the business flow.
            log.warn("Could not cache order {}: {}", response.orderRef(), e.getMessage());
        }
    }

    private Optional<OrderResponse> readFromCache(String orderRef) {
        try {
            String json = redisTemplate.opsForValue().get(ORDER_CACHE_PREFIX + orderRef);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, OrderResponse.class));
        } catch (Exception e) {
            log.warn("Could not read order {} from cache: {}", orderRef, e.getMessage());
            return Optional.empty();
        }
    }
}
