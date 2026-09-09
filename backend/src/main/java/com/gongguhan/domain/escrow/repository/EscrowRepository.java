package com.gongguhan.domain.escrow.repository;

import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.entity.EscrowStatus;
import java.util.List;
import java.util.Optional;
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
           select coalesce(sum(e.amount), 0) from Escrow e
            where e.groupBuyId = :groupBuyId
              and e.status = com.gongguhan.domain.escrow.entity.EscrowStatus.HELD
           """)
    long sumHeldAmountByGroupBuy(@Param("groupBuyId") Long groupBuyId);

    /** 플랫폼 전체 보관 총액 (FR-ADM-12). */
    @Query("""
           select coalesce(sum(e.amount), 0) from Escrow e
            where e.status = com.gongguhan.domain.escrow.entity.EscrowStatus.HELD
           """)
    long sumHeldAmount();

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
