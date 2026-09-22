package com.gongguhan.domain.payment.crypto.repository;

import com.gongguhan.domain.payment.crypto.entity.CryptoDeposit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CryptoDepositRepository extends JpaRepository<CryptoDeposit, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT d FROM CryptoDeposit d WHERE d.depositId=:id")
    Optional<CryptoDeposit> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM CryptoDeposit d, CryptoAsset a
             WHERE d.chainId = :chainId AND a.chainId = d.chainId
               AND LOWER(a.tokenContract) = LOWER(d.tokenContract)
               AND d.status IN ('UNMATCHED', 'UNDERPAID', 'OVERPAID')
               AND d.confirmations < a.requiredConfirmations
             ORDER BY d.depositId
            """)
    List<CryptoDeposit> findUnconfirmedRefundCandidates(
            @org.springframework.data.repository.query.Param("chainId") long chainId,
            org.springframework.data.domain.Pageable page);

    /**
     * UNIQUE(tx_hash, log_index) 와 짝을 이뤄 중복 기록을 막는다.
     *
     * <p>이 조회만으로는 동시 요청을 막지 못한다. 최종 방어선은 DB 제약이고,
     * 여기서 걸리는 것은 흔한 경우(재요청·재시도)를 예외 없이 처리하기 위함이다.
     */
    boolean existsByTxHashAndLogIndex(String txHash, int logIndex);

    Optional<CryptoDeposit> findByTxHashAndLogIndex(String txHash, int logIndex);

    List<CryptoDeposit> findByPaymentId(Long paymentId);

    /** 관리자 확인 대상 — 미매칭·초과·미달 입금 (RFD 연계). */
    List<CryptoDeposit> findByStatusInOrderByDetectedAtAsc(List<String> statuses);
}
