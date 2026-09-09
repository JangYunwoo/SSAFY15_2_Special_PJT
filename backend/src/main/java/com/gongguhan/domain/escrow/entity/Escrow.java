package com.gongguhan.domain.escrow.entity;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 에스크로. 구매자가 낸 돈을 구매확정 전까지 플랫폼이 보관하는 상태.
 *
 * <p><b>실제 자금 예치가 아니라 내부 원장입니다.</b> 실 자금 보관·정산은 전자금융거래법상
 * 규제 대상이라, 이 프로젝트는 "이 돈을 언제 판매자에게 보낼지"를 상태로만 관리합니다.
 * 실제 이체는 정산 시점에 SSAFY 금융망으로 한 번 일어납니다.
 *
 * <p>참여 건당 정확히 하나 존재합니다({@code UNIQUE(participation_id)}).
 * 이 유일성이 FR-ESC-08 불변식의 전제입니다.
 *
 * <blockquote>에스크로 보관 총액 = 미해제 참여 건의 결제금액 합계 (항상)</blockquote>
 *
 * <p><b>상태 전이 규칙을 서비스가 아니라 엔티티에 둔 이유.</b> 해제·환불을 부르는 경로가
 * 여러 개입니다 — 정산 배치, 관리자 수동 처리, 반품 처리, 모집 실패 처리. 규칙이
 * 서비스에 흩어지면 그중 하나가 빠뜨립니다. 엔티티가 막으면 어느 경로로 와도 통과하지
 * 못합니다.
 *
 * <p>팀 규칙상 JPA 연관관계를 쓰지 않아 참여 건을 {@code participationId} 로만 압니다.
 * 그래서 "구매확정됐는지"는 호출 측이 판정해 넘겨줍니다
 * ({@link com.gongguhan.domain.escrow.service.EscrowService} 참고).
 */
@Entity
@Table(name = "escrows")
public class Escrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "escrow_id")
    private Long escrowId;

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    /** 공동구매별 보관 총액 집계용 비정규화 (FR-ESC-04). */
    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    /** KRW 기준. 코인으로 결제한 건도 원화로 보관합니다 (FR-STL-10). */
    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscrowStatus status;

    @Column(name = "held_at", nullable = false)
    private LocalDateTime heldAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    protected Escrow() {
    }

    private Escrow(Long participationId, Long groupBuyId, long amount, LocalDateTime heldAt) {
        this.participationId = participationId;
        this.groupBuyId = groupBuyId;
        this.amount = amount;
        this.status = EscrowStatus.HELD;
        this.heldAt = heldAt;
    }

    /**
     * 결제 완료 즉시 보관 상태로 만듭니다 (FR-ESC-01, DR-32).
     *
     * <p>참여 건 생성과 <b>같은 트랜잭션</b>에서 호출해야 합니다. 따로 처리하면
     * "결제는 됐는데 에스크로가 없는" 구간이 생기고, 그 순간 FR-ESC-08 불변식이 깨집니다.
     *
     * @param heldAt 보관 시각. {@code Clock} 빈에서 가져온 값을 넘깁니다.
     *               엔티티가 직접 {@code LocalDateTime.now()} 를 부르면 테스트에서
     *               시각을 고정할 수 없고, 프로젝트의 UTC/KST 혼용 문제에 얽힙니다.
     */
    public static Escrow hold(Long participationId, Long groupBuyId, long amount,
                               LocalDateTime heldAt) {
        return new Escrow(participationId, groupBuyId, amount, heldAt);
    }

    /**
     * 정산을 위한 해제 (FR-ESC-02, DR-33).
     *
     * <p>구매확정이 선행 조건입니다. 모집 완료나 송장 등록만으로는 풀리지 않습니다
     * (FR-ESC-03). 이 검사를 통과하지 못하면 정산 대상이 될 수 없습니다(DR-34).
     *
     * @param purchaseConfirmed 해당 참여 건이 구매확정됐는지. 호출 측이 판정해 넘깁니다.
     * @throws BusinessException {@code INVALID_STATE_TRANSITION} — 이미 해제·환불된 건이거나
     *         아직 구매확정 전인 경우
     */
    public void release(boolean purchaseConfirmed, LocalDateTime releasedAt) {
        requireHeld();
        if (!purchaseConfirmed) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "구매확정되지 않아 에스크로를 해제할 수 없습니다. participationId="
                            + participationId);
        }
        this.status = EscrowStatus.RELEASED;
        this.releasedAt = releasedAt;
    }

    /**
     * 환불로 종료 (FR-ESC-05).
     *
     * <p>모집 실패, 참여 취소, 구매확정 전 반품, 판매자 귀책 무산이 해당합니다.
     * 이미 해제된 건은 자금이 나갔으므로 여기로 올 수 없습니다 — 그 경우는 상계
     * 또는 회수 채권 문제입니다.
     */
    public void refund(LocalDateTime refundedAt) {
        requireHeld();
        this.status = EscrowStatus.REFUNDED;
        this.refundedAt = refundedAt;
    }

    /**
     * 보관 중이 아니면 거부합니다.
     *
     * <p>이중 해제·이중 환불을 막는 지점입니다. 정산 배치를 재실행해도 같은 건이 두 번
     * 해제되지 않아야 합니다(NFR-OP-03).
     */
    private void requireHeld() {
        if (!status.isHeld()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "이미 %s 상태인 에스크로입니다. escrowId=%d".formatted(status, escrowId));
        }
    }

    public boolean isHeld() {
        return status.isHeld();
    }

    public Long getEscrowId() {
        return escrowId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public long getAmount() {
        return amount;
    }

    public EscrowStatus getStatus() {
        return status;
    }

    public LocalDateTime getHeldAt() {
        return heldAt;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }
}
