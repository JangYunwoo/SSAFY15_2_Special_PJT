package com.gongguhan.domain.payment.card.adapter;

import com.gongguhan.domain.payment.card.entity.CardPayment;
import com.gongguhan.domain.payment.card.repository.CardPaymentRepository;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.payment.card.service.SsafyCardApiClient;
import com.gongguhan.domain.payment.common.port.CardRefundPort;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link CardRefundPort}의 SSAFY 카드 구현 (S15P21B205-108).
 *
 * <p>{@link com.gongguhan.domain.payment.card.service.CardPaymentService#approve}와 같은
 * 원자성 규칙을 따른다 - 외부 호출은 트랜잭션 밖, 결과 기록은 짧은 저장으로.
 *
 * <p>취소 불가 판정(S15P21B205-34):
 * <ul>
 *   <li>부분 환불 - 취소 API에 금액 필드가 없어 전액만 가능</li>
 *   <li>청구서 발행 - A1058 응답. 결제일 + CARD_BILLING_CUTOFF_DAYS 경과 시 호출 전에 미리 판정</li>
 * </ul>
 * 두 경우 모두 REQUIRES_TRANSFER_FALLBACK으로 반환하고 계좌이체 전환은 호출자 몫이다.
 */
@Component
public class CardRefundAdapter implements CardRefundPort {

    private static final Logger log = LoggerFactory.getLogger(CardRefundAdapter.class);
    private static final DateTimeFormatter TRANSACTION_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String RESPONSE_CODE_BILLING_ISSUED = "A1058";
    private static final String TARGET_TYPE_PAYMENT = "PAYMENT";

    private final CardPaymentRepository cardPaymentRepository;
    private final SsafyCardApiClient ssafyCardApiClient;
    private final SsafyApiLogService ssafyApiLogService;
    private final PolicyService policyService;
    private final Clock clock;

    public CardRefundAdapter(CardPaymentRepository cardPaymentRepository,
                              SsafyCardApiClient ssafyCardApiClient,
                              SsafyApiLogService ssafyApiLogService,
                              PolicyService policyService,
                              Clock clock) {
        this.cardPaymentRepository = cardPaymentRepository;
        this.ssafyCardApiClient = ssafyCardApiClient;
        this.ssafyApiLogService = ssafyApiLogService;
        this.policyService = policyService;
        this.clock = clock;
    }

    @Override
    public CardRefundResult cancel(CardRefundCommand command) {
        CardPayment cardPayment = cardPaymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "카드 결제 내역을 찾을 수 없습니다."));

        // 멱등: 이미 전액 취소된 건은 외부를 다시 부르지 않고 같은 결과를 돌려준다.
        if (cardPayment.isCancelSucceeded()) {
            return CardRefundResult.canceled(amountOf(cardPayment), null, "이미 취소된 결제입니다.");
        }
        if (command.requestedAmount() != amountOf(cardPayment)) {
            return CardRefundResult.requiresTransferFallback(null,
                    "SSAFY 카드 취소는 전액만 가능합니다. 부분 환불은 계좌이체로 처리해야 합니다.");
        }
        if (cardPayment.isBillingIssued() || billingCutoffPassed(cardPayment)) {
            return CardRefundResult.requiresTransferFallback(null,
                    "청구서 발행(추정) 거래는 카드 취소가 불가합니다. 계좌이체로 처리해야 합니다.");
        }
        if (cardPayment.getTransactionUniqueNo() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "원거래 거래고유번호가 없어 취소할 수 없습니다. paymentId=" + command.paymentId());
        }

        String institutionCode = policyService.getString(PolicyKey.SSAFY_INSTITUTION_CODE);
        String fintechAppNo = policyService.getString(PolicyKey.SSAFY_FINTECH_APP_NO);
        String institutionTxNo = ssafyApiLogService
                .generateInstitutionTransactionUniqueNo(SsafyCardApiClient.CANCEL_API_SERVICE_CODE);

        // 외부 호출은 DB 트랜잭션 밖에서 수행한다(원자성 규칙 5번).
        SsafyCardApiClient.SsafyCardTransactionResponse response = ssafyCardApiClient.deleteTransaction(
                institutionTxNo, institutionCode, fintechAppNo, cardPayment.getSsafyUserKey(),
                command.cardNumber(), command.cvc(), cardPayment.getTransactionUniqueNo());

        ssafyApiLogService.record(SsafyCardApiClient.CANCEL_API_SERVICE_CODE, institutionTxNo,
                TARGET_TYPE_PAYMENT, command.paymentId(),
                new SanitizedCancelSnapshot(SsafyCardApiClient.CANCEL_API_SERVICE_CODE,
                        cardPayment.getTransactionUniqueNo(), command.requestedAmount()),
                response.header().responseCode(), response.header().responseMessage(), response);

        String responseCode = response.header().responseCode();
        String responseMessage = response.header().responseMessage();

        if (response.isSuccess()) {
            cardPayment.markCanceled(institutionTxNo, LocalDateTime.now(clock));
            cardPaymentRepository.save(cardPayment);
            return CardRefundResult.canceled(amountOf(cardPayment), responseCode, responseMessage);
        }
        if (RESPONSE_CODE_BILLING_ISSUED.equals(responseCode)) {
            cardPayment.markBillingIssued(LocalDateTime.now(clock));
            cardPaymentRepository.save(cardPayment);
            return CardRefundResult.requiresTransferFallback(responseCode, responseMessage);
        }

        log.warn("SSAFY 카드 취소 실패 paymentId={} responseCode={}", command.paymentId(), responseCode);
        cardPayment.markCancelFailed(institutionTxNo);
        cardPaymentRepository.save(cardPayment);
        return CardRefundResult.failed(responseCode, responseMessage);
    }

    private long amountOf(CardPayment cardPayment) {
        return cardPayment.getPaymentBalance() == null ? 0L : cardPayment.getPaymentBalance();
    }

    /**
     * 결제일 + CARD_BILLING_CUTOFF_DAYS(청구서 발행 추정 경과일)가 지났으면 A1058이
     * 확실시되므로 외부 호출 없이 폴백으로 보낸다. 판정 불가(날짜 누락·형식 오류)면
     * 보수적으로 호출해 본다 - A1058이면 그때 기록된다.
     */
    private boolean billingCutoffPassed(CardPayment cardPayment) {
        if (cardPayment.getTransactionDate() == null) {
            return false;
        }
        try {
            LocalDate transactionDate = LocalDate.parse(cardPayment.getTransactionDate(), TRANSACTION_DATE_FORMAT);
            int cutoffDays = policyService.getInt(PolicyKey.CARD_BILLING_CUTOFF_DAYS);
            return !LocalDate.now(clock).isBefore(transactionDate.plusDays(cutoffDays));
        } catch (DateTimeParseException e) {
            log.warn("카드 결제일 형식 오류로 청구서 발행 사전 판정 생략 paymentId={} transactionDate={}",
                    cardPayment.getPaymentId(), cardPayment.getTransactionDate());
            return false;
        }
    }

    /** 로그용 요청 스냅샷 - 카드번호·CVC는 담지 않는다. */
    private record SanitizedCancelSnapshot(String apiServiceCode, Long transactionUniqueNo, long requestedAmount) {
    }
}
