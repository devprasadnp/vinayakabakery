package com.harish.pickles.repository;

import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.OrderStatus;
import com.harish.pickles.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByUserAndArchivedFalseOrderByIdDesc(User user);

    List<Order> findByUserOrderByIdDesc(User user);

    List<Order> findByDeliveryPartnerAndArchivedFalseOrderByIdDesc(User deliveryPartner);

    Page<Order> findByArchivedFalseOrderByIdDesc(Pageable pageable);

    Page<Order> findByStatusAndArchivedFalseOrderByIdDesc(OrderStatus status, Pageable pageable);

    List<Order> findByArchivedTrueOrderByArchivedAtDesc();

    long countByStatusAndArchivedFalse(OrderStatus status);

    long countByArchivedFalse();

    long countByArchivedTrue();

    @Query("SELECT COALESCE(SUM(o.totalPaise), 0) FROM Order o "
            + "WHERE o.archived = false AND o.status IN :statuses")
    long sumTotalPaiseByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);
}
