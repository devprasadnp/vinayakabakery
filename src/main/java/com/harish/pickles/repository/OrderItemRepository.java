package com.harish.pickles.repository;

import com.harish.pickles.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** Guards permanent product deletion: a food in someone's order history must stay. */
    long countByProductId(Long productId);
}
