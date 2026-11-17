package com.tracker.ordertracking.repository;

import com.tracker.ordertracking.entity.Order;
import com.tracker.ordertracking.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data generates the SQL for these method names at startup.
 * findByOrderRef -> SELECT * FROM orders WHERE order_ref = ?
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderRef(String orderRef);

    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    boolean existsByOrderRef(String orderRef);
}
