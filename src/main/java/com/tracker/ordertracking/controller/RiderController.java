package com.tracker.ordertracking.controller;

import com.tracker.ordertracking.dto.RiderLocationMessage;
import com.tracker.ordertracking.dto.RiderLocationRequest;
import com.tracker.ordertracking.service.RiderLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Rider-facing API. This is the highest-traffic controller in the system:
 * one call per rider every 3 seconds.
 */
@RestController
@RequestMapping("/api/v1/riders")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*")   // demo only
public class RiderController {

    private final RiderLocationService riderLocationService;

    /**
     * POST /api/v1/riders/{riderId}/location
     * Accepts a GPS ping, stores it in Redis with a TTL and fans it out.
     * Returns 200 with the stored payload so the rider app can confirm delivery.
     */
    @PostMapping("/{riderId}/location")
    public ResponseEntity<RiderLocationMessage> updateLocation(@PathVariable String riderId,
                                                               @Valid @RequestBody RiderLocationRequest request) {
        return ResponseEntity.ok(riderLocationService.updateLocation(riderId, request));
    }

    /** GET /api/v1/riders/{riderId}/location - latest known position (404 if stale). */
    @GetMapping("/{riderId}/location")
    public ResponseEntity<RiderLocationMessage> getLocation(@PathVariable String riderId) {
        return ResponseEntity.ok(riderLocationService.getLatestLocation(riderId));
    }

    /** GET /api/v1/riders/{riderId}/trail - last 100 positions, newest first. */
    @GetMapping("/{riderId}/trail")
    public ResponseEntity<List<RiderLocationMessage>> getTrail(@PathVariable String riderId) {
        return ResponseEntity.ok(riderLocationService.getTrail(riderId));
    }
}
