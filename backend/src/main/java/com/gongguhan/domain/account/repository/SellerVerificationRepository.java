package com.gongguhan.domain.account.repository;

import com.gongguhan.domain.account.entity.SellerVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerVerificationRepository extends JpaRepository<SellerVerification, Long> {

    /** 특정 유형의 가장 최근 인증 이력(전환 자격 판정·확인 단계에서 사용). */
    Optional<SellerVerification> findTopByUserIdAndVerificationTypeOrderByCreatedAtDesc(
            Long userId, String verificationType);
}
