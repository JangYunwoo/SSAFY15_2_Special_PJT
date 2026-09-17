package com.gongguhan.domain.refund.port;

/**
 * 환불 도메인이 참여 건의 마무리를 요청하기 위한 경계 계약.
 *
 * <p>참여 취소({@code ParticipationCancelService})가 환불 실행을 이 도메인에 위임하므로,
 * 거꾸로 참여 서비스를 주입받으면 순환 참조가 된다. 포트를 두고 참여 도메인이 구현한다 —
 * {@code ParticipationWriterPort}, {@code GroupBuySlotPort} 와 같은 이유다.
 */
public interface ParticipationRefundPort {

    /**
     * 환불이 끝난 참여 건을 종료 상태로 넘긴다.
     *
     * <p><b>{@code REFUNDING} 인 건만 바꾼다.</b> 참여 취소는 {@code PAID -> REFUNDING} 으로
     * 옮겨두고 환불을 맡기므로 여기서 이어받아 끝낼 수 있다. 반면 반품 환불은 참여가
     * {@code RETURNING} 이고 수거·검수 등 남은 단계가 있어, 환불이 끝났다고 참여까지
     * 종료시키면 그 절차를 건너뛴다. 그 상태 기계는 반품 도메인이 관리한다.
     *
     * <p>대상이 아니면 조용히 넘어간다. 송금은 이미 끝났으므로 여기서 예외를 올리면
     * 호출 측이 환불 실패로 보고 재처리해 두 번 지급된다.
     */
    void finishRefundingParticipation(Long participationId);
}
