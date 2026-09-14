package com.gongguhan.domain.escrow.repository;

import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.entity.EscrowStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EscrowRepository extends JpaRepository<Escrow, Long> {

    /** 참여 건당 하나뿐이므로 단건이다 ({@code UNIQUE(participation_id)}). */
    Optional<Escrow> findByParticipationId(Long participationId);

    List<Escrow> findByGroupBuyIdAndStatus(Long groupBuyId, EscrowStatus status);

    List<Escrow> findByGroupBuyIdOrderByEscrowIdAsc(Long groupBuyId);

    /**
     * 공동구매별 보관 총액 (FR-ESC-04).
     *
     * <p>{@code coalesce} 로 감싸는 이유: 대상 행이 없으면 {@code sum} 이 NULL 을 돌려주고
     * {@code long} 반환 타입에서 NPE 가 난다.
     */
    @Query("""
           select coalesce(sum(e.amount - e.refundedAmount), 0) from Escrow e
            where e.groupBuyId = :groupBuyId
              and e.status = com.gongguhan.domain.escrow.entity.EscrowStatus.HELD
           """)
    long sumHeldAmountByGroupBuy(@Param("groupBuyId") Long groupBuyId);

    /**
     * 플랫폼 전체 보관 총액 (FR-ADM-12).
     *
     * <p>부분 환불로 빠져나간 금액을 뺍니다. {@code amount} 만 더하면 이미 돌려준 돈을
     * 아직 들고 있는 것처럼 보고하게 됩니다.
     */
    @Query("""
           select coalesce(sum(e.amount - e.refundedAmount), 0) from Escrow e
            where e.status = com.gongguhan.domain.escrow.entity.EscrowStatus.HELD
           """)
    long sumHeldAmount();

    /**
     * 환불로 빠져나간 총액 (FR-ADM-12).
     *
     * <p>상태별 집계로는 부분 환불이 보이지 않습니다. 42,000원을 환불하고 12,000원이
     * 남은 에스크로는 여전히 {@code HELD} 라, 상태만 세면 그 42,000원이 어디에도
     * 나타나지 않습니다. 환불 총액은 상태가 아니라 이 누계로 봐야 합니다.
     */
    @Query("select coalesce(sum(e.refundedAmount), 0) from Escrow e")
    long sumRefundedAmount();

    /** ESC-02 목록. 페이지로 끊되 합계는 별도 집계 쿼리로 전체를 센다. */
    Page<Escrow> findByGroupBuyId(Long groupBuyId, Pageable pageable);

    /**
     * 공동구매의 상태별 건수·금액 집계 (ESC-02, FR-ESC-04·08).
     *
     * <p>목록을 페이지로 끊어도 합계는 <b>전체</b>여야 한다. FR-ESC-08 이
     * "보관 총액 = 미해제 참여 건 결제금액 합계"를 요구하므로, 한 페이지만 더한 값은
     * 그 불변식을 확인하는 데 쓸 수 없다.
     *
     * <p>상태별로 한 행씩 돌아온다. 해당 상태의 행이 하나도 없으면 그 상태는
     * 결과에 나타나지 않으므로, 호출 측이 0으로 채워야 한다.
     */
    @Query("""
           select e.status as status, count(e) as count, coalesce(sum(e.amount), 0) as amount
             from Escrow e
            where e.groupBuyId = :groupBuyId
            group by e.status
           """)
    List<StatusAggregate> aggregateByGroupBuy(@Param("groupBuyId") Long groupBuyId);

    /** {@link #aggregateByGroupBuy} 결과 한 행. Spring Data 인터페이스 프로젝션. */
    interface StatusAggregate {

        EscrowStatus getStatus();

        long getCount();

        long getAmount();
    }

    /**
     * 플랫폼 전체 상태별 건수·금액 (ESC-03, FR-ADM-12).
     *
     * <p>{@link #aggregateByGroupBuy} 의 전역 버전이다. 관리자 대시보드가 한 번에
     * 보관액·해제액·환불액을 봐야 하므로 상태별로 한 번에 집계한다.
     */
    @Query("""
           select e.status as status, count(e) as count, coalesce(sum(e.amount), 0) as amount
             from Escrow e
            group by e.status
           """)
    List<StatusAggregate> aggregateAll();

    /**
     * 정산까지 끝난 금액 (ESC-03, FR-ADM-12).
     *
     * <p>에스크로 {@code RELEASED} 는 종료 상태라 정산 이후에도 바뀌지 않는다. 그래서
     * "풀렸지만 아직 판매자에게 나가지 않은 돈"과 "이미 나간 돈"을 에스크로만으로는
     * 구분할 수 없다. 참여 건 상태를 함께 봐야 한다.
     *
     * <blockquote>정산 대기 = RELEASED 전체 − 이 쿼리 결과</blockquote>
     *
     * <p>{@code participations.settled} 컬럼이 아니라 {@code status} 를 보는 이유:
     * 그 컬럼은 {@code Participation} 엔티티에 매핑되어 있지 않고, 해당 패키지는
     * 다른 담당자 소유라 매핑을 임의로 추가하지 않았다. 정산 완료 시 상태가
     * {@code SETTLED} 로 가는 것은 schema v2 의 상태 정의에 명시돼 있다.
     *
     * <p>팀 규칙상 JPA 연관관계를 쓰지 않으므로 ID 로 조인한다.
     */
    @Query("""
           select count(e) as count, coalesce(sum(e.amount), 0) as amount
             from Escrow e, com.gongguhan.domain.participation.entity.Participation p
            where e.participationId = p.participationId
              and e.status = com.gongguhan.domain.escrow.entity.EscrowStatus.RELEASED
              and p.status = 'SETTLED'
           """)
    CountAmount aggregateSettled();

    /** 건수와 금액만 담는 집계 결과. */
    interface CountAmount {

        long getCount();

        long getAmount();
    }

    /**
     * 불변식 위반 건을 찾는다 (FR-ESC-08, NFR-DT-05).
     *
     * <blockquote>에스크로 보관 총액 = 미해제 참여 건의 결제금액 합계</blockquote>
     *
     * <p>정상이라면 <b>항상 빈 결과</b>여야 한다. 결과가 나오면 결제 확정 경로 어딘가에서
     * 금액이 어긋나게 기록된 것이다.
     *
     * <p>팀 규칙상 JPA 연관관계를 쓰지 않으므로 ID 로 조인한다.
     */
    @Query("""
           select e from Escrow e, com.gongguhan.domain.participation.entity.Participation p
            where e.participationId = p.participationId
              and e.status = com.gongguhan.domain.escrow.entity.EscrowStatus.HELD
              and e.amount <> p.totalAmount
           """)
    List<Escrow> findAmountMismatches();

    /**
     * 참여 건이 없는 에스크로 (고아 행).
     *
     * <p>FK 가 있어 정상 경로로는 생길 수 없지만, 수동 SQL 로 참여 건을 지우면 남는다.
     * 관리자 점검용이다.
     */
    @Query("""
           select e from Escrow e
            where not exists (
                select 1 from com.gongguhan.domain.participation.entity.Participation p
                 where p.participationId = e.participationId
            )
           """)
    List<Escrow> findOrphans();
}
