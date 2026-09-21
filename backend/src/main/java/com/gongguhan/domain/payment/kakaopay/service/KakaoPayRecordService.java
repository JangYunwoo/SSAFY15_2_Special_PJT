package com.gongguhan.domain.payment.kakaopay.service;

import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.kakaopay.entity.KakaoPayPayment;
import com.gongguhan.domain.payment.kakaopay.repository.KakaoPayPaymentRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카카오페이 상세 행의 짧은 데이터베이스 트랜잭션을 담당한다. */
@Service
public class KakaoPayRecordService {

    private static final String METHOD_KAKAOPAY = "KAKAOPAY";

    private final KakaoPayPaymentRepository repository;
    private final PaymentService paymentService;
    private final Clock clock;

    public KakaoPayRecordService(KakaoPayPaymentRepository repository,
                                 PaymentService paymentService,
                                 Clock clock) {
        this.repository = repository;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReusableReady findReusableReady(Long paymentId) {
        KakaoPayPayment detail = repository.findById(paymentId).orElse(null);
        if (detail == null || !detail.hasReusableReady(LocalDateTime.now(clock))) {
            return null;
        }
        return new ReusableReady(
                detail.getTid(), detail.getNextRedirectPcUrl(), detail.getStateNonce(),
                detail.getStateExpiresAt());
    }

    @Transactional
    public void recordReady(Long paymentId, String tid, String partnerOrderId,
                            String partnerUserId, long totalAmount, String nextRedirectPcUrl,
                            String stateNonce, LocalDateTime stateExpiresAt) {
        KakaoPayPayment detail = repository.findForUpdate(paymentId)
                .orElseGet(() -> KakaoPayPayment.forPayment(paymentId));
        detail.recordReady(tid, partnerOrderId, partnerUserId, totalAmount,
                nextRedirectPcUrl, stateNonce, stateExpiresAt);
        repository.saveAndFlush(detail);
    }

    @Transactional(readOnly = true)
    public ApprovalRecord getApprovalRecord(Long paymentId, String nonce) {
        KakaoPayPayment detail = repository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "카카오페이 결제 준비 정보를 찾을 수 없습니다."));
        detail.verifyReady(nonce);
        return new ApprovalRecord(
                detail.getTid(), detail.getPartnerOrderId(), detail.getPartnerUserId(),
                detail.getTotalAmount());
    }

    @Transactional(readOnly = true)
    public boolean isCompleted(Long paymentId, String nonce) {
        return repository.findById(paymentId)
                .map(detail -> detail.isApprovedWithNonce(nonce))
                .orElse(false);
    }

    @Transactional
    public PaymentResponse complete(Long userId,
                                    KakaoPayStateSigner.StateClaims claims,
                                    KakaoPayGateway.ApproveResult approved) {
        KakaoPayPayment detail = repository.findForUpdate(claims.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "카카오페이 결제 준비 정보를 찾을 수 없습니다."));
        detail.verifyReady(claims.nonce());
        detail.verifyApproval(
                approved.tid(), approved.partnerOrderId(), approved.partnerUserId(),
                approved.totalAmount());

        PaymentResponse response = paymentService.completeOwnedPayment(
                userId, claims.paymentId(), METHOD_KAKAOPAY);
        detail.markApproved(
                approved.aid(), approved.vatAmount(), approved.paymentMethodType(),
                LocalDateTime.now(clock));
        return response;
    }

    /** 저장된 nonce 검증과 슬롯 종료·state 소비를 같은 짧은 트랜잭션에서 수행한다. */
    @Transactional
    public void endCallback(Long userId, KakaoPayStateSigner.StateClaims claims, String reason) {
        KakaoPayPayment detail = repository.findForUpdate(claims.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "카카오페이 결제 준비 정보를 찾을 수 없습니다."));
        detail.verifyReady(claims.nonce());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!detail.hasReusableReady(now)
                || !now.isBefore(LocalDateTime.ofEpochSecond(
                        claims.expiresAtEpochSecond(), 0, java.time.ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        }
        // 기존 승인 완료와 같은 상세 행 → 결제 행 잠금 순서로 처리한다.
        paymentService.abortPayment(userId, claims.paymentId(), METHOD_KAKAOPAY, reason);
        detail.consumeCallbackState(now);
    }

    public record ReusableReady(
            String tid,
            String nextRedirectPcUrl,
            String stateNonce,
            LocalDateTime stateExpiresAt
    ) {
    }

    public record ApprovalRecord(
            String tid,
            String partnerOrderId,
            String partnerUserId,
            long totalAmount
    ) {
    }
}
