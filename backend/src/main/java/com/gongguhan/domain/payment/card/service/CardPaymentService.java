package com.gongguhan.domain.payment.card.service;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.payment.card.dto.CardApproveRequest;
import com.gongguhan.domain.payment.card.entity.CardPayment;
import com.gongguhan.domain.payment.card.repository.CardPaymentRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * CARD-01 - 카드 승인 및 참여 확정 (FR-PAY-11, 13~14, IR-01).
 *
 * <p>원자성 규칙(공구한_API_명세서_v1.md §5)을 그대로 따른다: 외부 결제 호출({@link
 * SsafyCardApiClient})은 DB 트랜잭션 밖에서 하고, 승인 성공 후 짧은 트랜잭션에서
 * {@link PaymentService#completePayment}로 확정한다.
 *
 * <p>SSAFY 회원 연동이 필요하다. 외부 호출 전에 별도 트랜잭션으로 실행권을 기록하며,
 * 결과 불명은 기관거래고유번호로 대조할 때까지 재승인하지 않는다.
 */
@Service
public class CardPaymentService {

    private static final Logger log = LoggerFactory.getLogger(CardPaymentService.class);
    private static final String METHOD_CARD = "CARD";
    private static final String API_SERVICE_CODE = "createCreditCardTransaction";
    private static final String TARGET_TYPE_PAYMENT = "PAYMENT";

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final PaymentService paymentService;
    private final PolicyService policyService;
    private final SsafyCardApiClient ssafyCardApiClient;
    private final SsafyApiLogService ssafyApiLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CardApprovalRecordService approvalRecords;

    public CardPaymentService(PaymentRepository paymentRepository,
                               UserRepository userRepository,
                               UserAddressRepository userAddressRepository,
                               CardPaymentRepository cardPaymentRepository,
                               PaymentService paymentService,
                               PolicyService policyService,
                               SsafyCardApiClient ssafyCardApiClient,
                               SsafyApiLogService ssafyApiLogService,
                               ObjectMapper objectMapper,
                               Clock clock, CardApprovalRecordService approvalRecords) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.userAddressRepository = userAddressRepository;
        this.cardPaymentRepository = cardPaymentRepository;
        this.paymentService = paymentService;
        this.policyService = policyService;
        this.ssafyCardApiClient = ssafyCardApiClient;
        this.ssafyApiLogService = ssafyApiLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.approvalRecords = approvalRecords;
    }

    @Transactional(propagation = Propagation.NEVER)
    public void approve(Long userId, Long paymentId, CardApproveRequest request) {
        Payment payment = paymentRepository.findByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        validateApprovable(payment);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.getSsafyUserKey() == null || user.getSsafyUserKey().isBlank()) {
            throw new BusinessException(ErrorCode.SSAFY_MEMBER_NOT_LINKED);
        }

        long merchantId = policyService.getLong(PolicyKey.SSAFY_MERCHANT_ID);
        String institutionCode = policyService.getString(PolicyKey.SSAFY_INSTITUTION_CODE);
        String fintechAppNo = policyService.getString(PolicyKey.SSAFY_FINTECH_APP_NO);
        String institutionTxNo = ssafyApiLogService.generateInstitutionTransactionUniqueNo(API_SERVICE_CODE);

        // 별도 트랜잭션으로 실행권을 먼저 커밋한다. 외부 호출 중 DB 잠금은 유지하지 않는다.
        approvalRecords.claim(userId, paymentId, institutionTxNo);
        try {
            // 외부 호출은 DB 트랜잭션 밖에서 수행한다(원자성 규칙 5번).
            SsafyCardApiClient.SsafyCardTransactionResponse response = ssafyCardApiClient.createCreditCardTransaction(
                    institutionTxNo, institutionCode, fintechAppNo, user.getSsafyUserKey(),
                    request.cardNumber(), request.cvc(), merchantId, payment.getKrwAmount());

            ssafyApiLogService.record(API_SERVICE_CODE, institutionTxNo, TARGET_TYPE_PAYMENT, paymentId,
                    new SanitizedRequestSnapshot(API_SERVICE_CODE, merchantId, payment.getKrwAmount()),
                    response.header().responseCode(), response.header().responseMessage(), response);

            if (!response.isSuccess()) {
                log.warn("SSAFY 카드 승인 실패 paymentId={} responseCode={}", paymentId, response.header().responseCode());
                approvalRecords.recordOutcome(paymentId, "FAILED");
                paymentService.failPayment(paymentId, response.header().responseCode());
                throw new BusinessException(ErrorCode.EXTERNAL_PAYMENT_ERROR, response.header().responseMessage());
            }

            cardPaymentRepository.save(buildCardPayment(payment, user, request, response.rec(), institutionTxNo));

            UserAddress address = userAddressRepository.findById(payment.getAddressId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "배송지를 찾을 수 없습니다."));
            // 원자성 규칙 6번 - 승인 성공 후 짧은 트랜잭션에서 참여·에스크로를 함께 확정한다.
            paymentService.completePayment(paymentId, new PaymentService.ParticipationSnapshot(
                    address.getRecipientName(), address.getRecipientPhone(),
                    address.getZipcode(), address.getAddress1(), address.getAddress2()));
        } catch (RuntimeException exception) {
            // 결과 기록이 실패해도 먼저 커밋한 PROCESSING 실행권이 재승인을 차단한다.
            try {
                approvalRecords.recordOutcome(paymentId, "UNKNOWN");
            } catch (RuntimeException recordFailure) {
                exception.addSuppressed(recordFailure);
            }
            throw exception;
        }
    }

    private void validateApprovable(Payment payment) {
        if (!METHOD_CARD.equals(payment.getMethod())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "카드 결제 대상이 아닙니다.");
        }
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        if (payment.isExpired(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        }
    }

    private CardPayment buildCardPayment(Payment payment, User user, CardApproveRequest request,
                                          SsafyCardApiClient.SsafyCardTransactionResponse.Rec rec,
                                          String institutionTxNo) {
        return CardPayment.create(
                payment.getPaymentId(),
                user.getSsafyUserKey(),
                CardPayment.maskCardNumber(request.cardNumber()),
                parseLong(rec.merchantId()),
                rec.merchantName(),
                rec.categoryId(),
                institutionTxNo,
                parseLong(rec.transactionUniqueNo()),
                rec.transactionDate(),
                rec.transactionTime(),
                parseLong(rec.paymentBalance()),
                writeJson(rec));
    }

    private static Long parseLong(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    private String writeJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    /** 로그용 요청 스냅샷 - 카드번호·CVC는 담지 않는다. */
    private record SanitizedRequestSnapshot(String apiServiceCode, long merchantId, long paymentBalance) {
    }
}
