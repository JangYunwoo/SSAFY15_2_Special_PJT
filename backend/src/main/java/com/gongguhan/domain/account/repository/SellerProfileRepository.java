package com.gongguhan.domain.account.repository;

import com.gongguhan.domain.account.entity.SellerProfile;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    /** 상품 등록·공동구매 생성 쪽의 판매 가능 여부 확인용. */
    boolean existsBySellerIdAndStatusIn(Long sellerId, Collection<String> statuses);
}
