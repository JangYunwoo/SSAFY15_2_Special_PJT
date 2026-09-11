package com.gongguhan.domain.payment.common.repository;

import com.gongguhan.domain.payment.common.entity.Payment;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByGroupBuyIdAndUserIdAndStatus(Long groupBuyId, Long userId, String status);

    Optional<Payment> findByPaymentIdAndUserId(Long paymentId, Long userId);

    Optional<Payment> findByParticipationId(Long participationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId AND p.userId = :userId")
    Optional<Payment> findForUpdateByPaymentIdAndUserId(
            @Param("paymentId") Long paymentId, @Param("userId") Long userId);

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    // idx_payments_slot_expire(status, slot_expires_at) 사용
    List<Payment> findByStatusAndSlotExpiresAtBefore(String status, LocalDateTime slotExpiresAt);
}
