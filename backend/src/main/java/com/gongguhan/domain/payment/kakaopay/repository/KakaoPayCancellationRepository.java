package com.gongguhan.domain.payment.kakaopay.repository;

import com.gongguhan.domain.payment.kakaopay.entity.KakaoPayCancellation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoPayCancellationRepository
        extends JpaRepository<KakaoPayCancellation, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM KakaoPayCancellation c WHERE c.idempotencyKey = :idempotencyKey")
    Optional<KakaoPayCancellation> findForUpdate(
            @Param("idempotencyKey") String idempotencyKey);
}
