package com.gongguhan.domain.payment.kakaopay.service;

import com.gongguhan.domain.payment.kakaopay.entity.KakaoPayCancellation;
import com.gongguhan.domain.payment.kakaopay.entity.KakaoPayPayment;
import com.gongguhan.domain.payment.kakaopay.repository.KakaoPayCancellationRepository;
import com.gongguhan.domain.payment.kakaopay.repository.KakaoPayPaymentRepository;
import com.gongguhan.domain.returnrequest.port.RefundPort.RefundCommand;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 취소 외부 호출 전후의 멱등 상태를 별도 트랜잭션으로 보존한다. */
@Service
public class KakaoPayCancellationRecordService {

    private final KakaoPayCancellationRepository cancellationRepository;
    private final KakaoPayPaymentRepository paymentRepository;
    private final Clock clock;

    public KakaoPayCancellationRecordService(
            KakaoPayCancellationRepository cancellationRepository,
            KakaoPayPaymentRepository paymentRepository,
            Clock clock) {
        this.cancellationRepository = cancellationRepository;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Preparation prepare(RefundCommand command) {
        validate(command);
        KakaoPayPayment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "카카오페이 결제 정보를 찾을 수 없습니다."));
        if (payment.getAid() == null || payment.getTid() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "승인 완료된 카카오페이 결제만 취소할 수 있습니다.");
        }
        if (payment.getTotalAmount() == null || command.amount() > payment.getTotalAmount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "카카오페이 취소 금액이 원 결제 금액을 초과합니다.");
        }

        KakaoPayCancellation existing = cancellationRepository
                .findForUpdate(command.idempotencyKey()).orElse(null);
        if (existing != null) {
            existing.verifySameRequest(command.paymentId(), command.amount());
            if (existing.isSucceeded()) {
                return Preparation.cached(existing);
            }
            if (existing.isInFlightOrUnknown()) {
                return Preparation.uncertain(existing);
            }
        }

        KakaoPayCancellation cancellation = KakaoPayCancellation.start(
                command.idempotencyKey(), command.paymentId(), payment.getTid(),
                command.amount(), LocalDateTime.now(clock));
        cancellationRepository.saveAndFlush(cancellation);
        return Preparation.invoke(cancellation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(String idempotencyKey, KakaoPayGateway.CancelResult result) {
        KakaoPayCancellation cancellation = findForUpdate(idempotencyKey);
        if (!cancellation.getTid().equals(result.tid())
                || result.approvedCancelAmount() != cancellation.getRequestedAmount()
                || (!"CANCEL_PAYMENT".equals(result.status())
                && !"PART_CANCEL_PAYMENT".equals(result.status()))) {
            throw new BusinessException(ErrorCode.EXTERNAL_PAYMENT_ERROR,
                    "카카오페이 취소 결과가 요청 정보와 일치하지 않습니다.");
        }
        cancellation.markSucceeded(result.aid(), result.approvedCancelAmount(),
                result.canceledAmount(), result.cancelAvailableAmount(), LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String idempotencyKey, String reason, boolean outcomeUnknown) {
        KakaoPayCancellation cancellation = findForUpdate(idempotencyKey);
        if (outcomeUnknown) {
            cancellation.markUnknown(reason, LocalDateTime.now(clock));
        } else {
            cancellation.markFailed(reason, LocalDateTime.now(clock));
        }
    }

    private KakaoPayCancellation findForUpdate(String idempotencyKey) {
        return cancellationRepository.findForUpdate(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "카카오페이 취소 요청 정보를 찾을 수 없습니다."));
    }

    private void validate(RefundCommand command) {
        if (!"KAKAOPAY".equals(command.paymentMethod()) || command.paymentId() == null
                || command.returnId() == null || command.amount() <= 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "카카오페이 취소 요청 정보가 올바르지 않습니다.");
        }
    }

    public record Preparation(KakaoPayCancellation cancellation, Outcome outcome) {

        static Preparation invoke(KakaoPayCancellation cancellation) {
            return new Preparation(cancellation, Outcome.INVOKE);
        }

        static Preparation cached(KakaoPayCancellation cancellation) {
            return new Preparation(cancellation, Outcome.CACHED_SUCCESS);
        }

        static Preparation uncertain(KakaoPayCancellation cancellation) {
            return new Preparation(cancellation, Outcome.OUTCOME_UNKNOWN);
        }

        public enum Outcome {
            INVOKE,
            CACHED_SUCCESS,
            OUTCOME_UNKNOWN
        }
    }
}
