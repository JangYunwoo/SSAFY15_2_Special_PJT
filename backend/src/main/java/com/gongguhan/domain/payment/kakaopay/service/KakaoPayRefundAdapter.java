package com.gongguhan.domain.payment.kakaopay.service;

import com.gongguhan.domain.payment.kakaopay.entity.KakaoPayCancellation;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayCancellationRecordService.Preparation;
import com.gongguhan.domain.returnrequest.port.RefundPort;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import org.springframework.stereotype.Component;

/** 반품 공통 환불 계약을 카카오페이 결제 취소 API로 변환한다. */
@Component
public class KakaoPayRefundAdapter implements RefundPort {

    private final KakaoPayCancellationRecordService recordService;
    private final KakaoPayGateway gateway;

    public KakaoPayRefundAdapter(KakaoPayCancellationRecordService recordService,
                                 KakaoPayGateway gateway) {
        this.recordService = recordService;
        this.gateway = gateway;
    }

    @Override
    public boolean supports(String paymentMethod) {
        return "KAKAOPAY".equals(paymentMethod);
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        Preparation preparation = recordService.prepare(command);
        KakaoPayCancellation cancellation = preparation.cancellation();
        if (preparation.outcome() == Preparation.Outcome.CACHED_SUCCESS) {
            return RefundResult.succeeded(cancellation.getCancelAid());
        }
        if (preparation.outcome() == Preparation.Outcome.OUTCOME_UNKNOWN) {
            return RefundResult.failed(
                    "이전 카카오페이 취소 요청의 결과를 확인해야 합니다.");
        }

        KakaoPayGateway.CancelResult result;
        try {
            result = gateway.cancel(
                    new KakaoPayGateway.CancelCommand(cancellation.getTid(),
                            cancellation.getRequestedAmount(), 0));
        } catch (BusinessException exception) {
            boolean outcomeUnknown = exception.getErrorCode() == ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE;
            recordService.fail(command.idempotencyKey(), exception.getMessage(), outcomeUnknown);
            throw exception;
        }

        try {
            recordService.succeed(command.idempotencyKey(), result);
            return RefundResult.succeeded(result.aid());
        } catch (BusinessException exception) {
            // 제공사 취소는 성공했지만 응답 검증/저장에 실패한 경우 재호출하면 이중 취소가 된다.
            recordService.fail(command.idempotencyKey(), exception.getMessage(), true);
            throw exception;
        }
    }
}
