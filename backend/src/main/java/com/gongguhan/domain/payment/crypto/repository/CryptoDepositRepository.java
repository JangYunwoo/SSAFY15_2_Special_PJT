package com.gongguhan.domain.payment.crypto.repository;

import com.gongguhan.domain.payment.crypto.entity.CryptoDeposit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CryptoDepositRepository extends JpaRepository<CryptoDeposit, Long> {

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
