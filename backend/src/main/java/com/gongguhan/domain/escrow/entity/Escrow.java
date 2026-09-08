package com.gongguhan.domain.escrow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 결제 승인 커밋 시 HELD 상태로 생성하는 최소 엔티티다.
 * release/refund 등 이후 생애주기는 이 티켓 범위 밖(에스크로 도메인 별도 작업)이다.
 */
@Entity
@Table(name = "escrows")
public class Escrow {

    public static final String STATUS_HELD = "HELD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "escrow_id")
    private Long escrowId;

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "held_at", nullable = false)
    private LocalDateTime heldAt;

    protected Escrow() {
    }

    private Escrow(Long participationId, Long groupBuyId, long amount) {
        this.participationId = participationId;
        this.groupBuyId = groupBuyId;
        this.amount = amount;
        this.status = STATUS_HELD;
        this.heldAt = LocalDateTime.now();
    }

    public static Escrow hold(Long participationId, Long groupBuyId, long amount) {
        return new Escrow(participationId, groupBuyId, amount);
    }

    public Long getEscrowId() {
        return escrowId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public String getStatus() {
        return status;
    }
}
