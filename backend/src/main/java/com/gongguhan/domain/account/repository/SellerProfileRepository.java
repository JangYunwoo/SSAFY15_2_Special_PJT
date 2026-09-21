package com.gongguhan.domain.account.repository;

import com.gongguhan.domain.account.entity.SellerProfile;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    /** 제재 누적과 프로필 수정 모두 같은 쓰기 잠금을 사용한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SellerProfile s where s.sellerId = :id")
    Optional<SellerProfile> findForUpdate(@Param("id") Long id);

    /** 상품 등록·공동구매 생성 쪽의 판매 가능 여부 확인용. */
    boolean existsBySellerIdAndStatusIn(Long sellerId, Collection<String> statuses);
}
