package com.gongguhan.domain.payment.kakaopay.repository;

import com.gongguhan.domain.payment.kakaopay.entity.KakaoPayPayment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoPayPaymentRepository extends JpaRepository<KakaoPayPayment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM KakaoPayPayment k WHERE k.paymentId = :paymentId")
    Optional<KakaoPayPayment> findForUpdate(@Param("paymentId") Long paymentId);
}
