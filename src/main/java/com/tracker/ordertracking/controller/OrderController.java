package com.tracker.ordertracking.controller;

import com.tracker.ordertracking.dto.AssignRiderRequest;
import com.tracker.ordertracking.dto.CreateOrderRequest;
import com.tracker.ordertracking.dto.OrderEventResponse;
import com.tracker.ordertracking.dto.OrderResponse;
import com.tracker.ordertracking.dto.UpdateStatusRequest;
import com.tracker.ordertracking.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Customer- and restaurant-facing REST API.
 *
 * Controllers here do exactly three things: bind the request, call the service,
 * shape the HTTP response. No business logic, no Redis, no SQL.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*")   // demo only
public class OrderController {

    private final OrderService orderService;

    /** POST /api/v1/orders - place a new order (starts at PLACED). */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + response.orderRef()))
                .body(response);
    }

    /** GET /api/v1/orders/{orderRef} - cache-aside read. */
    @GetMapping("/{orderRef}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderRef) {
        return ResponseEntity.ok(orderService.getOrder(orderRef));
    }

    /** GET /api/v1/orders?customerId=CUST-1 - a customer's order history. */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrdersForCustomer(@RequestParam String customerId) {
        return ResponseEntity.ok(orderService.getOrdersForCustomer(customerId));
    }

    /** PATCH /api/v1/orders/{orderRef}/status - move the state machine forward. */
    @PatchMapping("/{orderRef}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable String orderRef,
                                                      @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(orderRef, request));
    }

    /** PATCH /api/v1/orders/{orderRef}/rider - attach a rider to the order. */
    @PatchMapping("/{orderRef}/rider")
    public ResponseEntity<OrderResponse> assignRider(@PathVariable String orderRef,
                                                     @Valid @RequestBody AssignRiderRequest request) {
        return ResponseEntity.ok(orderService.assignRider(orderRef, request));
    }

    /** GET /api/v1/orders/{orderRef}/events - the full audit timeline. */
    @GetMapping("/{orderRef}/events")
    public ResponseEntity<List<OrderEventResponse>> getTimeline(@PathVariable String orderRef) {
        return ResponseEntity.ok(orderService.getOrderTimeline(orderRef));
    }

    /** Tiny convenience endpoint used by the demo page and the simulator. */
    @PostMapping("/{orderRef}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable String orderRef) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(orderService.updateStatus(orderRef,
                        new UpdateStatusRequest(com.tracker.ordertracking.entity.OrderStatus.CANCELLED,
                                "Cancelled by customer")));
    }
}
