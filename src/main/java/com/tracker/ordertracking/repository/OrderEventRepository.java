package com.tracker.ordertracking.repository;

import com.tracker.ordertracking.entity.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrderRefOrderByOccurredAtAsc(String orderRef);

    /** Idempotency guard for re-delivered Redis Stream records. */
    boolean existsByEventId(String eventId);
}
