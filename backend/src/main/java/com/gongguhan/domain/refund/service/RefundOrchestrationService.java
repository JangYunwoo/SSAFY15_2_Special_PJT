package com.gongguhan.domain.refund.service;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.admin.service.AuditLogService;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.port.CardRefundPort;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.entity.RefundMethod;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.entity.RefundStatus;
import com.gongguhan.domain.refund.service.RefundLedgerService.OpenResult;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.payment.crypto.service.CryptoRefundService;
import com.gongguhan.domain.returnrequest.port.RefundPort;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 실행 오케스트레이션 (S15P21B205-109).
 *
 * <p>원장({@link RefundLedgerService})은 "돌려주기로 했다" 를 기록할 뿐 돈을 보내지
 * 않습니다. 이 클래스가 <b>결제수단을 보고 어느 경로로 돌려줄지 정하고, 외부 호출을
 * 실행하고, 결과를 원장에 적습니다.</b>
 *
 * <h2>왜 카드 결제도 계좌이체로 돌려주는가</h2>
 *
 * <p>SSAFY 카드 취소({@code deleteTransaction})는 취소 요청마다 <b>카드번호와 CVC 를
 * 다시 요구</b>합니다. 보안 요건상 카드 원문은 어디에도 저장하지 않으므로, 사람이
 * 카드를 다시 입력하는 화면이 없는 <b>자동 환불은 카드 취소를 호출할 수 없습니다</b>.
 * 모집 실패 배치·반품 수거 완료·참여 취소가 전부 여기 해당합니다.
 *
 * <p>API 명세 11장이 "카드 승인 취소가 불가능한 경우 SSAFY 계좌이체 환불로 전환할 수
 * 있다" 고 허용하므로, 자동 환불은 처음부터 계좌이체로 갑니다. 구매자가 받는 금액은
 * 같습니다. 카드 취소를 쓰려면 카드 재입력을 받는 별도 유스케이스가 필요하고, 그건
 * 이 클래스가 아니라 화면이 있는 경로에서 {@code CardRefundPort} 를 직접 부르면 됩니다.
 *
 * <h2>트랜잭션 경계</h2>
 *
 * <p>클래스 전체에 트랜잭션을 적용하지 않습니다. 취소 의도 예약은 호출자의 트랜잭션에만
 * 참여하고, 예약 후 외부 실행은 트랜잭션이 없는 상태만 허용합니다. 외부 송금은 수 초가 걸리는데
 * DB 트랜잭션 안에서 부르면 커넥션과 잠금이 그만큼 묶입니다. 원장 쓰기는
 * {@link RefundLedgerService} 의 짧은 트랜잭션이 담당합니다. 코인 결제 검증에서
 * {@code CryptoTxVerificationService} / {@code CryptoTxRecordService} 를 나눈 것과
 * 같은 구조입니다.
 *
 * <h2>이중 송금 방지</h2>
 *
 * <p>최초 요청은 {@link OpenResult#created()} 가 true 일 때만 실행을 시도합니다. 미송신
 * 영속 예약의 복구도 DB 실행권을 획득한 한 요청만 송금합니다. 개시는 멱등이라 같은
 * 요청이 두 번 오면 기존 행이 돌아오는데, 그걸 구분하지 않으면 원장은 한 건인데
 * 돈이 두 번 나갑니다. 동시 요청 중 INSERT 에 성공하는 쪽은 DB 유일성 제약 덕에
 * 정확히 하나입니다.
 */
@Service
public class RefundOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(RefundOrchestrationService.class);

    /** 결제 수단 값. {@code payments.method} 의 CHECK 주석과 같은 값입니다. */
    private static final String METHOD_CARD = "CARD";
    private static final String METHOD_KAKAOPAY = "KAKAOPAY";
    private static final String METHOD_CRYPTO = "CRYPTO";

    /** {@code ssafy_api_logs.target_type}. 카드 결제는 PAYMENT 를 쓴다. */
    private static final String TARGET_TYPE_REFUND = "REFUND";

    private static final String WITHDRAWAL_SUMMARY = "공구한 환불 출금";
    private static final String DEPOSIT_SUMMARY = "공구한 환불 입금";

    private final RefundLedgerService ledgerService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SsafyTransferClient transferClient;
    private final SsafyApiLogService ssafyApiLogService;
    private final CryptoRefundService cryptoRefundService;
    private final AuditLogService auditLogService;
    private final PolicyService policyService;
    private final List<RefundPort> refundPorts;
    private final CardRefundPort cardRefundPort;
    private final RefundFinalizationService finalizationService;

    public RefundOrchestrationService(RefundLedgerService ledgerService,
                                      PaymentRepository paymentRepository,
                                      UserRepository userRepository,
                                      SsafyTransferClient transferClient,
                                      SsafyApiLogService ssafyApiLogService,
                                      CryptoRefundService cryptoRefundService,
                                      AuditLogService auditLogService,
                                      PolicyService policyService,
                                      List<RefundPort> refundPorts,
                                      CardRefundPort cardRefundPort,
                                      RefundFinalizationService finalizationService) {
        this.ledgerService = ledgerService;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.transferClient = transferClient;
        this.ssafyApiLogService = ssafyApiLogService;
        this.cryptoRefundService = cryptoRefundService;
        this.auditLogService = auditLogService;
        this.policyService = policyService;
        this.refundPorts = refundPorts;
        this.cardRefundPort = cardRefundPort;
        this.finalizationService = finalizationService;
    }

    /**
     * 참여 건 환불을 개시하고 실행합니다.
     *
     * <p>모집 실패·참여 취소·판매자 귀책이 이 경로입니다.
     *
     * @return 원장의 환불 건. 실행에 실패해도 행은 남고 {@code FAILED} 가 됩니다.
     */
    public Refund refundParticipation(Long participationId, long amount, RefundReason reason) {
        Payment payment = loadPayment(participationId);
        RefundMethod method = resolveMethod(payment.getMethod());

        OpenResult opened = ledgerService.openForParticipation(
                participationId, amount, reason, method);
        Refund refund = executeIfCreated(opened, payment);
        // 새로 만들어 실행한 건만 종결한다. 이미 있던 건은 이 요청이 실행하지 않았으므로
        // (executeIfCreated 가 건너뜀) 여기서 다시 에스크로를 닫으려 하면 안 된다.
        if (opened.created()) {
            finalizeIfParticipationCompleted(refund);
        }
        return refund;
    }

    /** 영속 원장의 미송신 작업 또는 완료 후 내부 종결만 복구한다. UNKNOWN/FAILED 재송금은 하지 않는다. */
    @Transactional(propagation = Propagation.NEVER)
    public Refund resumePersisted(Long refundId) {
        Refund refund = ledgerService.get(refundId);
        if (refund.isProcessing() && !refund.isExecutionClaimed()
                && refund.getParticipationId() != null && refund.getRefundMethod() != RefundMethod.CARD_CANCEL) {
            refund = execute(refund, loadPayment(refund.getParticipationId()));
        }
        finalizeIfParticipationCompleted(refund);
        return refund;
    }

    /** 참여 취소의 상태·인원 변경 트랜잭션에 원장 생성을 합류시킨다. 외부 호출은 없다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OpenResult reserveParticipationCancellation(Long participationId, long amount, String paymentMethod) {
        RefundMethod method = METHOD_CARD.equals(paymentMethod) ? RefundMethod.CARD_CANCEL : resolveMethod(paymentMethod);
        return ledgerService.openForParticipation(participationId, amount, RefundReason.PARTICIPATION_CANCEL, method);
    }

    /** 원장을 새로 예약한 요청만 커밋 후 실행한다. 외부 호출 중 DB 트랜잭션을 열지 않는다. */
    @Transactional(propagation = Propagation.NEVER)
    public Refund executeParticipationCancellation(Long participationId, OpenResult opened,
                                                  String cardNumber, String cvc) {
        if (!opened.created()) {
            return opened.refund();
        }
        Payment payment = loadPayment(participationId);
        Refund refund = opened.refund().getRefundMethod() == RefundMethod.CARD_CANCEL
                ? executeCardCancel(opened.refund(), payment, cardNumber, cvc)
                : executeIfCreated(opened, payment);
        finalizeIfParticipationCompleted(refund);
        return refund;
    }

    /**
     * 카드 결제 건을 <b>카드 승인 취소</b>로 환불합니다 (S15P21B205-108).
     *
     * <p>{@link #refundParticipation} 은 카드 재입력 화면이 없어 계좌이체로만 돌려주지만,
     * 참여 취소는 사용자가 화면에서 카드를 다시 입력할 수 있으므로 카드 취소를 직접
     * 시도합니다. 전액·청구서 발행 전이면 원결제가 카드에서 취소되고, 부분 환불이거나
     * 청구서가 발행된 건은 {@link CardRefundPort.CardRefundResult.Status#REQUIRES_TRANSFER_FALLBACK}
     * 로 알려져 계좌이체로 폴백합니다. 그래서 "왜 카드로 환불되지 않느냐"는 물음에 먼저
     * 카드 취소로 답하고, 정말 불가능한 경우에만 계좌이체로 넘어갑니다.
     *
     * @throws BusinessException 결제 수단이 카드가 아니면 {@code INVALID_STATE_TRANSITION}
     */
    public Refund refundParticipationByCard(Long participationId, long amount, RefundReason reason,
                                            String cardNumber, String cvc) {
        Payment payment = loadPayment(participationId);
        if (!METHOD_CARD.equals(payment.getMethod())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "카드 결제만 카드 취소로 환불할 수 있습니다. method=" + payment.getMethod());
        }

        OpenResult opened = ledgerService.openForParticipation(
                participationId, amount, reason, RefundMethod.CARD_CANCEL);
        if (!opened.created()) {
            log.info("이미 진행 중이거나 완료된 환불이라 카드 취소를 건너뜁니다. refundId={}",
                    opened.refund().getRefundId());
            return opened.refund();
        }
        Refund refund = executeCardCancel(opened.refund(), payment, cardNumber, cvc);
        finalizeIfParticipationCompleted(refund);
        return refund;
    }

    /**
     * 카드 취소를 실행하고, 취소 불가면 계좌이체로 폴백합니다.
     *
     * <p>외부 호출은 {@link RefundOrchestrationService} 규약대로 트랜잭션 밖입니다.
     * 카드번호·CVC 는 어디에도 저장하지 않고 이 호출 안에서만 씁니다.
     */
    private Refund executeCardCancel(Refund refund, Payment payment, String cardNumber, String cvc) {
        Long refundId = refund.getRefundId();
        if (!ledgerService.tryClaimExecution(refundId, null)) { return ledgerService.get(refundId); }
        CardRefundPort.CardRefundResult result;
        try {
            result = cardRefundPort.cancel(new CardRefundPort.CardRefundCommand(
                    payment.getPaymentId(), cardNumber, cvc, refund.getAmount()));
        } catch (BusinessException exception) {
            log.warn("카드 취소 실행 실패 refundId={} reason={}", refundId, exception.getMessage());
            return ledgerService.fail(refundId, truncate(exception.getMessage()));
        }

        return switch (result.status()) {
            case CANCELED -> ledgerService.completeByCardCancel(refundId, null);
            case REQUIRES_TRANSFER_FALLBACK -> {
                ledgerService.releaseUnsentOrRejectedExecution(refundId);
                log.info("카드 취소 불가로 계좌이체 폴백 refundId={} reason={}",
                        refundId, result.responseMessage());
                // 전환된 엔티티(수단=ACCOUNT_TRANSFER)를 넘긴다. 넘기지 않고 기존 refund 를
                // 다시 쓰면 in-memory 수단이 CARD_CANCEL 이라 execute 가 잘못 분기한다.
                Refund switched = ledgerService.fallbackToAccountTransfer(refundId);
                yield execute(switched, payment);
            }
            case FAILED -> failKnownRejected(refundId, truncate(
                    result.responseMessage() == null ? "카드 취소가 거절되었습니다." : result.responseMessage()));
        };
    }

    /** 수거 상태와 같은 트랜잭션에서 예약된 환불만 커밋 후 실행한다. */
    @Transactional(propagation = Propagation.NEVER)
    public Refund executeReservedReturn(Long participationId, OpenResult reserved) {
        Refund result = executeIfCreated(reserved, loadPayment(participationId));
        finalizeIfParticipationCompleted(result);
        return result;
    }

    /** 반품 승인·수거 완료에 따른 환불을 개시하고 실행합니다 (FR-RTN-17). */
    public Refund refundReturn(Long returnId, Long participationId, long amount) {
        Payment payment = loadPayment(participationId);
        RefundMethod method = resolveMethod(payment.getMethod());

        OpenResult opened = ledgerService.openForReturn(returnId, participationId, amount, method);
        Refund result = executeIfCreated(opened, payment);
        finalizeIfParticipationCompleted(result);
        return result;
    }

    /**
     * 실패한 환불을 다시 실행합니다 (RFD-04, FR-ADM-08).
     *
     * <p>원장을 {@code PROCESSING} 으로 되돌린 뒤 같은 경로로 재실행합니다.
     * 기관거래고유번호는 매번 새로 채번합니다 — 같은 값을 다시 쓰면 금융망이 중복
     * 거래로 거부합니다(H1007).
     */
    /**
     * 관리자가 직접 재처리합니다 (RFD-04).
     *
     * <p>돈을 다시 내보내는 지점이라 <b>누가 눌렀는지</b>를 감사 로그에 남깁니다.
     * 결과가 실패여도 시도는 기록합니다 - 이중 지급 의혹이 생겼을 때 "몇 번 눌렸나" 가
     * 가장 먼저 필요한 정보입니다.
     */
    public Refund retry(Long adminId, Long refundId) {
        Refund result = retry(refundId);
        auditLogService.record(adminId, AuditLogService.TARGET_REFUND, refundId,
                AuditLogService.ACTION_RETRY,
                AuditLogService.values("status", RefundStatus.FAILED.name()),
                AuditLogService.values("status", result.getStatus().name(),
                        "amount", result.getAmount(),
                        "refundMethod", result.getRefundMethod().name()));
        return result;
    }

    /** 재처리 본체. 감사 로그가 필요 없는 내부 호출용입니다. */
    public Refund retry(Long refundId) {
        Refund refund = ledgerService.retry(refundId);
        Long participationId = refund.getParticipationId();
        if (participationId == null) {
            // 입금 단위 환불(슬롯 만료·미매칭)은 결제 건이 없어 자산을 특정할 수 없다.
            // 미매칭 입금 환불은 코인 결제 도메인이 직접 개시·송금한다.
            return ledgerService.fail(refundId,
                    "입금 단위 환불은 코인 결제 도메인에서 처리합니다.");
        }
        Refund result = execute(refund, loadPayment(participationId));
        finalizeIfParticipationCompleted(result);
        return result;
    }

    private Refund executeIfCreated(OpenResult opened, Payment payment) {
        if (!opened.created()) {
            log.info("이미 진행 중이거나 완료된 환불이라 송금을 건너뜁니다. refundId={}",
                    opened.refund().getRefundId());
            return opened.refund();
        }
        return execute(opened.refund(), payment);
    }

    /**
     * 결제수단에 맞는 환불 수단을 정합니다.
     *
     * <p>카드는 위 클래스 주석의 이유로 계좌이체가 됩니다. 코인은 온체인 송금 어댑터가
     * 아직 없어 {@link RefundMethod#CRYPTO} 로 두고 실행 단계에서 실패로 남깁니다.
     * 임의로 원화 계좌이체로 바꾸지 않는 이유는, 구매자가 USDT 로 냈는데 원화를
     * 받게 되는 것이 <b>환율 변동 손익을 누가 지는지</b>를 건드리는 제품 결정이기
     * 때문입니다. 팀이 정하기 전까지 조용히 통화를 바꾸지 않습니다.
     */
    private RefundMethod resolveMethod(String paymentMethod) {
        return switch (paymentMethod) {
            case METHOD_KAKAOPAY -> RefundMethod.KAKAOPAY;
            case METHOD_CARD -> RefundMethod.ACCOUNT_TRANSFER;
            case METHOD_CRYPTO -> RefundMethod.CRYPTO;
            default -> throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "환불할 수 없는 결제수단입니다. method=" + paymentMethod);
        };
    }

    private Refund execute(Refund refund, Payment payment) {
        Long refundId = refund.getRefundId();
        // 목적지 기록과 사전조건 실패 처리도 실행권 소유자만 한다. 패자가 승자 상태를 덮지 못한다.
        if (!ledgerService.tryClaimExecution(refundId, null)) { return ledgerService.get(refundId); }
        try {
            Refund executed = switch (refund.getRefundMethod()) {
                case KAKAOPAY -> refundByPort(refund, payment);
                case ACCOUNT_TRANSFER -> refundByTransfer(refund, payment);
                case CRYPTO -> refundByCrypto(refund, payment);
                case CARD_CANCEL -> failKnownRejected(refundId,
                        "카드 취소는 카드 재입력이 필요해 자동 환불로 처리할 수 없습니다.");
            };
            return executed;
        } catch (BusinessException exception) {
            // 외부 연동 실패는 재처리 대상으로 남긴다. 사유를 그대로 적어 관리자가 판단한다.
            log.warn("환불 실행 실패 refundId={} reason={}", refundId, exception.getMessage());
            return ledgerService.fail(refundId, truncate(exception.getMessage()));
        }
    }

    /**
     * 코인으로 결제한 건을 코인으로 돌려줍니다 (S15P21B205-80).
     *
     * <p>원화로 보관하고 있으므로 <b>환불 시점 환율</b>로 그만큼의 코인을 지급합니다.
     * 구매자가 코인 가격 변동을 지고, 플랫폼은 원화 기준으로 정확합니다.
     *
     * <p>수취 주소를 <b>송금 전에</b> 원장에 기록합니다. 송금 직후 장애가 나면 어디로
     * 보냈는지 남아 있어야 체인에서 대조할 수 있습니다.
     *
     * <p><b>결과를 알 수 없는 실패는 자동 재시도하면 안 됩니다.</b> 송금이 이미
     * 나갔을 수 있어 두 번 지급됩니다. 환불 재처리(RFD-04)는 관리자가 직접 누르는
     * 경로라 사람이 개입하지만, 사유에 확인이 필요하다는 것을 남깁니다.
     */
    private Refund refundByCrypto(Refund refund, Payment payment) {
        Long refundId = refund.getRefundId();
        if (!cryptoRefundService.isEnabled()) {
            return failKnownRejected(refundId,
                    "환불 지갑 개인키가 설정되지 않아 코인 환불을 보낼 수 없습니다.");
        }

        String buyerWallet = cryptoRefundService.findBuyerWallet(payment.getPaymentId());
        ledgerService.assignWalletAddress(refundId, buyerWallet);

        CryptoRefundService.RefundTransfer result;
        try {
            result = cryptoRefundService.refundByPayment(
                    payment.getPaymentId(), refund.getAmount());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE) {
                // 송금이 나갔는지 알 수 없다. 재처리 전에 체인 확인이 필요하다.
                log.error("코인 환불 결과 불명 refundId={} to={} - 체인에서 확인 후 재처리 필요",
                        refundId, buyerWallet, exception);
                return ledgerService.fail(refundId,
                        "송금 결과를 확인하지 못했습니다. 체인에서 확인한 뒤 재처리하세요: "
                                + exception.getMessage());
            }
            throw exception;
        }

        log.info("코인 환불 완료 refundId={} txHash={} 수량={} 환율={}",
                refundId, result.txHash(), result.assetAmount().toPlainString(),
                result.appliedRate().toPlainString());
        return ledgerService.completeByCrypto(refundId, result.txHash(), result.assetAmount());
    }

    /** 카카오페이처럼 제공사 취소 어댑터가 있는 수단. */
    private Refund refundByPort(Refund refund, Payment payment) {
        RefundPort port = refundPorts.stream()
                .filter(candidate -> candidate.supports(payment.getMethod()))
                .findFirst()
                .orElse(null);
        if (port == null) {
            return failKnownRejected(refund.getRefundId(),
                    "지원하는 환불 어댑터가 없습니다: " + payment.getMethod());
        }

        RefundPort.RefundCommand command = new RefundPort.RefundCommand(
                idempotencyKeyOf(refund), refund.getReturnId(), payment.getPaymentId(),
                payment.getMethod(), refund.getAmount());
        RefundPort.RefundResult result = port.refund(command);

        if (result.status() == RefundPort.RefundResult.Status.SUCCEEDED) {
            return ledgerService.completeByKakaoPay(refund.getRefundId(), result.providerRefundId());
        }
        return ledgerService.fail(refund.getRefundId(), truncate(result.failureReason()));
    }

    /**
     * 플랫폼 계좌에서 구매자 계좌로 이체합니다.
     *
     * <p>구매자 계좌가 없으면 실행하지 않고 실패로 남깁니다. 조용히 넘어가면 구매자는
     * 환불이 진행 중인 줄 알고 기다리게 됩니다.
     */
    private Refund refundByTransfer(Refund refund, Payment payment) {
        String platformAccount = policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO);
        if (platformAccount == null || platformAccount.isBlank()) {
            return failKnownRejected(refund.getRefundId(),
                    "플랫폼 출금 계좌가 설정되지 않았습니다.");
        }

        User buyer = userRepository.findById(payment.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "구매자를 찾을 수 없습니다. userId=" + payment.getUserId()));
        String buyerAccount = buyer.getSsafyAccountNo();
        if (buyerAccount == null || buyerAccount.isBlank()) {
            return failKnownRejected(refund.getRefundId(),
                    "환불 수취 계좌가 등록되지 않았습니다. 프로필에서 계좌를 등록해야 합니다.");
        }
        if (buyer.getSsafyUserKey() == null || buyer.getSsafyUserKey().isBlank()) {
            return failKnownRejected(refund.getRefundId(),
                    "SSAFY 금융망 회원 연동이 완료되지 않았습니다.");
        }

        ledgerService.assignAccount(refund.getRefundId(), buyerAccount);

        // 호출 전에 채번한다. 응답을 못 받아도 이 번호로 금융망 거래를 되짚을 수 있다.
        String institutionTxNo = ssafyApiLogService.generateInstitutionTransactionUniqueNo(
                SsafyTransferClient.API_SERVICE_CODE);
        ledgerService.recordInstitutionTransaction(refund.getRefundId(), institutionTxNo);

        SsafyTransferClient.TransferResponse response;
        try {
            response = transferClient.transfer(institutionTxNo,
                    policyService.getString(PolicyKey.SSAFY_INSTITUTION_CODE),
                    policyService.getString(PolicyKey.SSAFY_FINTECH_APP_NO),
                    buyer.getSsafyUserKey(), platformAccount, buyerAccount,
                    refund.getAmount(), WITHDRAWAL_SUMMARY, DEPOSIT_SUMMARY);
        } catch (BusinessException exception) {
            // 응답을 받지 못했다. 이체가 실제로 일어났는지 알 수 없으므로 자동 재시도하면
            // 이중 지급 위험이 있다. 실패로 남기고 관리자가 금융망 거래내역을 확인한다.
            ssafyApiLogService.record(SsafyTransferClient.API_SERVICE_CODE, institutionTxNo,
                    TARGET_TYPE_REFUND, refund.getRefundId(),
                    sanitized(platformAccount, buyerAccount, refund.getAmount()),
                    null, exception.getMessage(), null);
            throw exception;
        }

        ssafyApiLogService.record(SsafyTransferClient.API_SERVICE_CODE, institutionTxNo,
                TARGET_TYPE_REFUND, refund.getRefundId(),
                sanitized(platformAccount, buyerAccount, refund.getAmount()),
                response.header().responseCode(), response.header().responseMessage(), response);

        if (!response.isSuccess()) {
            return failKnownRejected(refund.getRefundId(),
                    truncate(response.header().responseMessage()));
        }
        return ledgerService.completeByAccountTransfer(refund.getRefundId(), institutionTxNo,
                response.firstTransactionUniqueNo());
    }

    private Refund failKnownRejected(Long refundId, String reason) {
        return ledgerService.failRejected(refundId, reason);
    }

    /** 로그에 남길 요청 스냅샷. 계좌번호는 뒤 4자리만 남깁니다. */
    private Object sanitized(String withdrawalAccountNo, String depositAccountNo, long amount) {
        return Map.of(
                "withdrawalAccountNo", mask(withdrawalAccountNo),
                "depositAccountNo", mask(depositAccountNo),
                "transactionBalance", String.valueOf(amount));
    }

    private String mask(String accountNo) {
        if (accountNo == null || accountNo.length() <= 4) {
            return "****";
        }
        return "*".repeat(accountNo.length() - 4) + accountNo.substring(accountNo.length() - 4);
    }

    /**
     * 제공사 어댑터에 넘길 멱등 키.
     *
     * <p>환불 ID 를 그대로 씁니다. 같은 환불 건의 재시도는 같은 키가 되어 제공사 쪽에서
     * 중복 취소를 걸러낼 수 있습니다.
     */
    private String idempotencyKeyOf(Refund refund) {
        return "refund-" + refund.getRefundId();
    }

    private Payment loadPayment(Long participationId) {
        return paymentRepository.findByParticipationId(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "환불할 결제 건을 찾을 수 없습니다. participationId=" + participationId));
    }

    /** {@code refunds.fail_reason} 이 VARCHAR(255) 라 넘치면 저장 시점에 깨진다. */
    private String truncate(String reason) {
        if (reason == null) {
            return "사유가 기록되지 않았습니다.";
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }

    /**
     * 환불이 완료됐으면 에스크로를 닫고 참여를 종결한다.
     *
     * <p>참여 취소·모집 실패·판매자 귀책 무산이 대상이다. 반품({@code returnId} 있음)은 반품
     * 도메인이 스스로 종결하고, 입금 단위 코인 환불({@code participationId} 없음)은 참여가 없다.
     *
     * <p><b>환불 송금은 이미 완료·커밋된 상태다.</b> 마무리(에스크로/참여 종결)가 실패해도
     * 환불을 되돌리거나 호출자(참여 취소 요청)를 실패로 만들면 안 된다 — 로그만 남기고 넘어가,
     * 재처리(RFD-04)나 정합성 점검(FR-ESC invariant) 대상으로 둔다.
     */
    private void finalizeIfParticipationCompleted(Refund refund) {
        if (refund == null || refund.getStatus() != RefundStatus.COMPLETED) {
            return;
        }
        if (refund.getParticipationId() == null) {
            return;
        }
        Long participationId = refund.getParticipationId();
        try {
            if (refund.getReturnId() != null) {
                finalizationService.finalizeReturnRefund(refund);
            } else {
                finalizationService.finalizeParticipationRefund(participationId, "참여 환불 완료");
            }
        } catch (RuntimeException exception) {
            log.error("환불은 완료됐으나 에스크로/참여 종결에 실패했습니다. participationId={} refundId={}",
                    participationId, refund.getRefundId(), exception);
        }
    }

}
