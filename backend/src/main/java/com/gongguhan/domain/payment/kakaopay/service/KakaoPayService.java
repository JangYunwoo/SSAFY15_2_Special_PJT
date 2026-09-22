package com.gongguhan.domain.payment.kakaopay.service;

import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.kakaopay.dto.KakaoPayApproveRequest;
import com.gongguhan.domain.payment.kakaopay.dto.KakaoPayReadyResponse;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오페이 Ready/Approve 오케스트레이션.
 * 외부 HTTP 호출 동안 데이터베이스 트랜잭션을 열지 않는다.
 */
@Service
public class KakaoPayService {

    private static final String METHOD_KAKAOPAY = "KAKAOPAY";
    private static final int MAX_REDIRECT_URL_LENGTH = 255;

    private final PaymentService paymentService;
    private final KakaoPayRecordService recordService;
    private final KakaoPayGateway gateway;
    private final KakaoPayStateSigner stateSigner;
    private final KakaoPayProperties properties;

    public KakaoPayService(PaymentService paymentService,
                           KakaoPayRecordService recordService,
                           KakaoPayGateway gateway,
                           KakaoPayStateSigner stateSigner,
                           KakaoPayProperties properties) {
        this.paymentService = paymentService;
        this.recordService = recordService;
        this.gateway = gateway;
        this.stateSigner = stateSigner;
        this.properties = properties;
    }

    public KakaoPayReadyResponse ready(Long userId, Long paymentId) {
        PaymentService.ApprovalContext payment = paymentService.getApprovalContext(
                userId, paymentId, METHOD_KAKAOPAY);

        KakaoPayRecordService.ReusableReady reusable = recordService.findReusableReady(paymentId);
        if (reusable != null) {
            KakaoPayStateSigner.StateClaims claims = new KakaoPayStateSigner.StateClaims(
                    paymentId, userId,
                    reusable.stateExpiresAt().toEpochSecond(ZoneOffset.UTC),
                    reusable.stateNonce());
            // 기존 Ready도 매번 같은 HMAC으로 재구성할 수 있어 URL 자체를 DB에 둘 필요가 없다.
            stateSigner.sign(claims);
            return response(paymentId, reusable.tid(), reusable.nextRedirectPcUrl(),
                    reusable.stateExpiresAt());
        }

        KakaoPayStateSigner.StateClaims claims = stateSigner.issue(
                paymentId, userId, payment.expiresAt());
        String state = stateSigner.sign(claims);
        String partnerOrderId = "PAYMENT-" + paymentId;
        String partnerUserId = String.valueOf(userId);

        String approvalUrl = redirectUrl("success", paymentId, state);
        String cancelUrl = redirectUrl("cancel", paymentId, state);
        String failUrl = redirectUrl("fail", paymentId, state);
        ensureRedirectLength(approvalUrl, cancelUrl, failUrl);

        KakaoPayGateway.ReadyResult ready = gateway.ready(new KakaoPayGateway.ReadyCommand(
                partnerOrderId,
                partnerUserId,
                "공동구매 #" + payment.groupBuyId(),
                payment.amount(),
                approvalUrl,
                cancelUrl,
                failUrl));

        // 외부 호출 사이에 슬롯이 만료되거나 다른 요청으로 종료되지 않았는지 다시 검증한다.
        paymentService.getApprovalContext(userId, paymentId, METHOD_KAKAOPAY);
        recordService.recordReady(
                paymentId, ready.tid(), partnerOrderId, partnerUserId, payment.amount(),
                ready.nextRedirectPcUrl(), claims.nonce(), payment.expiresAt());
        return response(paymentId, ready.tid(), ready.nextRedirectPcUrl(), payment.expiresAt());
    }

    public PaymentResponse approve(Long userId, KakaoPayApproveRequest request) {
        KakaoPayStateSigner.StateClaims claims = stateSigner.verify(request.state());
        if (!request.paymentId().equals(claims.paymentId()) || !userId.equals(claims.userId())) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }

        if (recordService.isCompleted(request.paymentId(), claims.nonce())) {
            return paymentService.getCompletedPayment(userId, request.paymentId(), METHOD_KAKAOPAY);
        }

        PaymentService.ApprovalContext payment = paymentService.getApprovalContext(
                userId, request.paymentId(), METHOD_KAKAOPAY);
        KakaoPayRecordService.ApprovalRecord ready = recordService.getApprovalRecord(
                request.paymentId(), claims.nonce());
        if (ready.totalAmount() != payment.amount()
                || !ready.partnerUserId().equals(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.EXTERNAL_PAYMENT_ERROR,
                    "카카오페이 결제 준비 정보가 결제 건과 일치하지 않습니다.");
        }

        KakaoPayGateway.ApproveResult approved = gateway.approve(
                new KakaoPayGateway.ApproveCommand(
                        ready.tid(), ready.partnerOrderId(), ready.partnerUserId(),
                        request.pgToken()));

        // 외부 응답의 TID·주문·사용자·금액 검증과 내부 상태 확정은 한 트랜잭션에서 재검증한다.
        PaymentResponse response = recordService.complete(userId, claims, approved);

        // 청구는 성공했으나 공구 종료로 확정 대신 환불이 예약된 경우: 상세 기록은 이미 커밋됐고,
        // 트랜잭션 밖인 여기서 사용자 대면 예외로 알린다.
        if (Payment.RECONCILE_REFUND_REQUIRED.equals(response.reconcileState())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_TERMINATED_REFUND_SCHEDULED);
        }
        return response;
    }

    public void cancelFlow(Long userId, Long paymentId, String state) {
        recordService.endCallback(userId, verifyCallback(userId, paymentId, state),
                "KAKAOPAY_USER_CANCELLED");
    }

    public void failFlow(Long userId, Long paymentId, String state) {
        recordService.endCallback(userId, verifyCallback(userId, paymentId, state),
                "KAKAOPAY_AUTH_FAILED");
    }

    private KakaoPayStateSigner.StateClaims verifyCallback(Long userId, Long paymentId, String state) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        KakaoPayStateSigner.StateClaims claims = stateSigner.verify(state);
        if (!paymentId.equals(claims.paymentId()) || !userId.equals(claims.userId())) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }
        return claims;
    }

    private KakaoPayReadyResponse response(Long paymentId, String tid, String redirectUrl,
                                           LocalDateTime expiresAt) {
        return new KakaoPayReadyResponse(
                String.valueOf(paymentId), tid, redirectUrl,
                expiresAt.atOffset(ZoneOffset.UTC).toString());
    }

    private String redirectUrl(String result, Long paymentId, String state) {
        return UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                .path("/checkout/kakaopay/")
                .path(result)
                .queryParam("paymentId", paymentId)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    private void ensureRedirectLength(String... urls) {
        for (String url : urls) {
            if (url.length() > MAX_REDIRECT_URL_LENGTH) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "카카오페이 리다이렉트 URL이 허용 길이를 초과했습니다.");
            }
        }
    }
}
