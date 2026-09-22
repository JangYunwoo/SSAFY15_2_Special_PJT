package com.gongguhan.domain.payment.crypto.repository;

import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CryptoPaymentRepository extends JpaRepository<CryptoPayment, Long> {

    /** tx_hash 는 UNIQUE. 같은 트랜잭션을 두 결제에 쓰려는 시도를 걸러낸다. */
    Optional<CryptoPayment> findByTxHash(String txHash);

    boolean existsByTxHash(String txHash);

    /**
     * 컨펌 폴링 대상 (5초 주기).
     *
     * <p>영수증이 성공으로 확인됐고 아직 필요 컨펌 수에 못 미친 건만 가져온다.
     * 결제 실패·만료와 입금 추적 종료는 다르다. 종료 결제도 이미 제출한 거래가
     * 충분히 확인될 때까지 추적하며, 참여를 만들지 않고 반환 대상으로 분류한다.
     *
     * <p>페이지로 끊는 이유: RPC 호출이 건당 1회 이상 발생한다. 밀린 건이 수백 개면
     * 한 주기에 다 처리하려다 노드 호출 한도에 걸리고, 5초 주기를 넘겨 다음 실행과
     * 겹친다.
     */
    @Query("""
           select cp from CryptoPayment cp
            where cp.txHash is not null
              and cp.receiptStatus = com.gongguhan.domain.payment.crypto.entity.CryptoPayment.RECEIPT_SUCCESS
              and cp.confirmations < cp.requiredConfirmations
              and cp.paymentId in (
                  select p.paymentId from Payment p
                   where p.status IN ('PROCESSING', 'EXPIRED', 'FAILED')
              )
            order by cp.paymentId asc
           """)
    List<CryptoPayment> findPendingConfirmation(Pageable pageable);

    /**
     * 영수증을 아직 확인하지 못한 건 (RPC 일시 실패 등으로 receipt_status 가 NULL).
     *
     * <p>구매자가 txHash 를 제출한 직후에는 노드가 아직 트랜잭션을 모를 수 있다.
     * 그 경우 CRYP-03 이 영수증 확인을 미루므로, 폴링이 이어받아 재시도한다.
     */
    @Query("""
           select cp from CryptoPayment cp
            where cp.txHash is not null
              and cp.receiptStatus is null
              and cp.paymentId in (
                  select p.paymentId from Payment p
                   where p.status IN ('PROCESSING', 'EXPIRED', 'FAILED')
              )
            order by cp.paymentId asc
           """)
    List<CryptoPayment> findAwaitingReceipt(Pageable pageable);
}
