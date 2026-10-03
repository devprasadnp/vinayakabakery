package com.harish.pickles.repository;

import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.Payment;
import com.harish.pickles.domain.PaymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByMerchantOrderId(String merchantOrderId);

    List<Payment> findByOrderOrderByIdDesc(Order order);

    List<Payment> findByMerchantOrderIdIn(Collection<String> merchantOrderIds);

    Optional<Payment> findByTransactionIdAndStatus(String transactionId, PaymentStatus status);
}
