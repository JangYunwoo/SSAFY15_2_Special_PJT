package com.gongguhan.domain.payment.common.port;

/**
 * 결제 공통 계층이 참여 건을 조회·생성하기 위한 경계 계약.
 *
 * <p>결제 공통은 이 계약에만 의존하며 참여 엔티티나 저장소를 직접 참조하지 않는다.
 * {@link GroupBuySlotPort}와 같은 이유로 존재한다.
 */
public interface ParticipationWriterPort {

    /** 동일 구매자가 이 공동구매에 이미 유효 참여 중인지. */
    boolean existsActiveParticipation(Long groupBuyId, Long userId);

    /** 결제 승인 성공 시 참여 건을 생성한다. */
    ParticipationRecord create(ParticipationCreateCommand command);

    record ParticipationCreateCommand(
            Long groupBuyId,
            Long userId,
            long shareAmount,
            long shippingFee,
            long totalAmount,
            String recipientName,
            String recipientPhone,
            String zipcode,
            String address1,
            String address2,
            String deliveryRequest
    ) {
        public ParticipationCreateCommand(Long groupBuyId, Long userId, long shareAmount, long shippingFee,
                long totalAmount, String recipientName, String recipientPhone, String zipcode,
                String address1, String address2) {
            this(groupBuyId, userId, shareAmount, shippingFee, totalAmount, recipientName, recipientPhone,
                    zipcode, address1, address2, null);
        }
    }

    record ParticipationRecord(
            Long participationId,
            Long groupBuyId,
            Long userId
    ) {
    }
}
