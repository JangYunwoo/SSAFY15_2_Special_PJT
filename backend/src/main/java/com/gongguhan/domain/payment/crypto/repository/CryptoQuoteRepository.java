package com.gongguhan.domain.payment.crypto.repository;

import com.gongguhan.domain.payment.crypto.entity.CryptoQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface CryptoQuoteRepository extends JpaRepository<CryptoQuote, Long> {

    /**
     * 견적을 원자적으로 claim한다 (배정된 동시성 과제: Quote 중복 사용 방지).
     *
     * <p>조회 후 검사하고 저장하는 방식은 두 요청이 같은 견적을 함께 통과시킨다.
     * {@code used = false} 조건을 UPDATE의 WHERE에 넣어 DB가 한 번만 성공하게 한다.
     * 만료 검사까지 같은 쿼리에 넣어야 "만료 직후 claim" 경합도 함께 닫힌다.
     *
     * <p>CRYP-03에서 사용한다. CRYP-02는 발급만 하므로 호출하지 않는다.
     *
     * @return 1이면 claim 성공, 0이면 이미 사용됐거나 만료됨
     */
    @Modifying
    @Query("""
           update CryptoQuote q
              set q.used = true
            where q.quoteId = :quoteId
              and q.used = false
              and q.expiresAt > :now
           """)
    int claim(@Param("quoteId") Long quoteId, @Param("now") LocalDateTime now);
}
