package com.gongguhan.domain.refund.repository;

import com.gongguhan.domain.payment.common.entity.Payment;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 승인 후 공구 종료로 고립된 결제(외부 청구 성공, 참여 확정 실패)의 환불 후보를 찾는다.
 *
 * <p>{@code reconcile_state='REFUND_REQUIRED'} 인 결제만 대상이다. 예약이 성공하면
 * {@code REFUND_RESERVED} 로 바뀌어 자연히 후보에서 빠지므로 재선정되지 않는다.
 * 카드는 계좌이체로, 카카오페이는 원거래 취소로 환불한다(코인 §7 별도 트랙 제외).
 */
public interface StrandedPaymentRefundRepository extends JpaRepository<Payment, Long> {

    @Query("""
            SELECT p.paymentId FROM Payment p
             WHERE p.reconcileState = 'REFUND_REQUIRED'
               AND p.method IN ('CARD', 'KAKAOPAY')
             ORDER BY p.paymentId
            """)
    List<Long> findCandidates(Pageable pageable);
}
