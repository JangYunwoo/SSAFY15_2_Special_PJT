package com.gongguhan.domain.penalty.repository;

import com.gongguhan.domain.penalty.entity.SellerPenalty;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerPenaltyRepository extends JpaRepository<SellerPenalty, Long> {

    /** 판정의 상태 확인과 변경을 하나로 직렬화한다. 다음 잠금은 판매자 행이다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SellerPenalty p where p.penaltyId = :id")
    Optional<SellerPenalty> findForUpdate(@Param("id") Long id);

    /** 위반 후보 중복 생성 방지 - 같은 공동구매·같은 사유는 상태와 무관하게 1건만 둔다. */
    boolean existsByGroupBuyIdAndReasonType(Long groupBuyId, String reasonType);

    /**
     * 판매자의 확정 위반 횟수 (PEN-01, FR-PEN-09~12).
     *
     * <p><b>누적의 기준은 이 값입니다.</b> {@code seller_profiles.penalty_count} 는 상품
     * 등록·공동구매 생성이 매번 조인 없이 확인하려고 둔 비정규화 값이라, 어긋나면
     * 이 쿼리가 정답입니다.
     *
     * <p>후보(CANDIDATE)는 세지 않습니다. 배치와 구매자 이의 제기가 자동으로 쌓는
     * 것이라 사람 판단 없이 정지시킬 수 없습니다.
     */
    long countBySellerIdAndStatus(Long sellerId, String status);

    /** PEN-01 판매자 본인 위반 이력. 최근 순. */
    Page<SellerPenalty> findBySellerIdOrderByDetectedAtDesc(Long sellerId, Pageable pageable);

    /** PEN-02 관리자 목록. 상태 필터 없이 전체. */
    Page<SellerPenalty> findAllByOrderByDetectedAtDesc(Pageable pageable);

    /** PEN-02 관리자 목록. 상태로 거른다 - 검토 대기(CANDIDATE)만 보는 것이 주된 쓰임이다. */
    Page<SellerPenalty> findByStatusOrderByDetectedAtDesc(String status, Pageable pageable);

    /** 같은 판매자의 확정 이력. 제재 근거를 함께 보여줄 때 쓴다. */
    List<SellerPenalty> findBySellerIdAndStatusOrderByDetectedAtDesc(Long sellerId, String status);
}
