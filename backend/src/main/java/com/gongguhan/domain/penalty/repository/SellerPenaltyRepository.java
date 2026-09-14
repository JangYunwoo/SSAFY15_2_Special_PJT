package com.gongguhan.domain.penalty.repository;

import com.gongguhan.domain.penalty.entity.SellerPenalty;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerPenaltyRepository extends JpaRepository<SellerPenalty, Long> {

    /** 위반 후보 중복 생성 방지 - 같은 공동구매·같은 사유는 상태와 무관하게 1건만 둔다. */
    boolean existsByGroupBuyIdAndReasonType(Long groupBuyId, String reasonType);
}
