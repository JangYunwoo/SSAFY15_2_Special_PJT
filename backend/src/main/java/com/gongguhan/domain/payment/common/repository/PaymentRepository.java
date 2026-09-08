package com.gongguhan.domain.payment.common.repository;

import com.gongguhan.domain.payment.common.entity.Payment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByGroupBuyIdAndUserIdAndStatus(Long groupBuyId, Long userId, String status);

    Optional<Payment> findByPaymentIdAndUserId(Long paymentId, Long userId);

    Page<Payment> findByUserId(Long userId, Pageable pageable);
}
