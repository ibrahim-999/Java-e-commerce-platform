package com.ecommerce.paymentservice.repository;

import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findByUserId(Long userId);

    Optional<Payment> findByTransactionId(String transactionId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
