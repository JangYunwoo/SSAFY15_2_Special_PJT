package com.gongguhan.domain.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 정산에 포함된 참여 건 (FR-STL-07~08).
 *
 * <p><b>{@code UNIQUE(participation_id)} 가 이중 정산의 최종 방어선입니다.</b>
 * 배치를 두 번 돌리거나 두 회차가 겹쳐도 같은 참여 건이 두 정산에 들어갈 수 없습니다.
 * 들어가면 판매자에게 같은 돈을 두 번 지급합니다. 애플리케이션에서 조회로 거르는 것만으로는
 * 두 배치가 동시에 조회를 통과할 수 있어 부족합니다.
 *
 * <p><b>제외된 참여 건은 여기에 행을 만들지 않습니다.</b> 반품·미확정으로 빠진 건은
 * 조회 시점에 "이 공동구매의 참여 건 중 정산 항목에 없는 것" 으로 계산합니다. 제외를
 * 행으로 남기면 같은 참여 건이 회차마다 제외 행으로 쌓여, 나중에 정산될 때
 * {@code UNIQUE} 와 충돌합니다.
 */
@Entity
@Table(name = "settlement_items", uniqueConstraints =
        // V1 이 MySQL 에 만드는 제약과 같은 것을 엔티티에도 선언한다. 테스트는 Flyway 가
        // 아니라 엔티티에서 H2 스키마를 만들기 때문에, 여기 없으면 "이중 정산이 DB 에서
        // 막힌다" 를 테스트로 확인할 수 없다. ddl-auto=validate 는 제약을 검사하지 않아
        // 운영 스키마와 충돌하지 않는다.
        @UniqueConstraint(name = "uk_settlement_items_participation",
                columnNames = "participation_id"))
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    /** 상품 분담금. 수수료 기준 금액에 들어갑니다. */
    @Column(name = "share_amount", nullable = false)
    private long shareAmount;

    /** 배송비. 수수료를 매기지 않습니다. */
    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    protected SettlementItem() {
    }

    private SettlementItem(Long settlementId, Long participationId,
                           long shareAmount, long shippingFee) {
        this.settlementId = settlementId;
        this.participationId = participationId;
        this.shareAmount = shareAmount;
        this.shippingFee = shippingFee;
    }

    public static SettlementItem of(Long settlementId, Long participationId,
                                    long shareAmount, long shippingFee) {
        return new SettlementItem(settlementId, participationId, shareAmount, shippingFee);
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getSettlementId() {
        return settlementId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public long getShareAmount() {
        return shareAmount;
    }

    public long getShippingFee() {
        return shippingFee;
    }
}
