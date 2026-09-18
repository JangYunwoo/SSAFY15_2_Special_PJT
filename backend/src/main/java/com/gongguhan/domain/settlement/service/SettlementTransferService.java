package com.gongguhan.domain.settlement.service;

import com.gongguhan.domain.account.entity.SellerProfile;
import com.gongguhan.domain.admin.service.AuditLogService;
import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.SellerProfileRepository;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.refund.service.SsafyTransferClient;
import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 정산 계좌이체 실행 (STL-04·07, FR-STL-01~05, 12).
 *
 * <p>플랫폼 계좌에서 판매자 계좌로 정산금을 보냅니다. 환불에서 쓰는
 * {@link SsafyTransferClient} 를 그대로 재사용하며 방향만 반대입니다.
 *
 * <h2>트랜잭션 경계</h2>
 *
 * <p>이 클래스에는 트랜잭션이 없습니다. 이체 호출은 수 초가 걸리는데 DB 트랜잭션 안에서
 * 부르면 커넥션과 잠금이 그만큼 묶입니다. 기록은
 * {@link SettlementRecordService} 의 짧은 트랜잭션이 담당합니다.
 *
 * <h2>이중 이체를 막는 방법</h2>
 *
 * <p>가장 위험한 경우는 <b>요청은 나갔는데 응답을 못 받은</b> 상황입니다. 실패로 보고
 * 자동 재시도하면 판매자에게 두 번 지급됩니다. 그래서 응답 없음은
 * {@code TRANSFER_UNKNOWN} 으로 따로 표시해 자동 재시도에서 빼고, 사람이 금융망
 * 거래내역을 확인한 뒤 처리하게 합니다.
 *
 * <p>기관거래고유번호는 <b>시도마다 새로 채번</b>합니다. 같은 번호를 다시 쓰면 금융망이
 * 중복 거래로 거부합니다(H1007).
 */
@Service
public class SettlementTransferService {

    private static final Logger log = LoggerFactory.getLogger(SettlementTransferService.class);

    /** {@code ssafy_api_logs.target_type}. */
    private static final String TARGET_TYPE_SETTLEMENT = "SETTLEMENT";

    private static final String WITHDRAWAL_SUMMARY = "공구한 정산 출금";
    private static final String DEPOSIT_SUMMARY = "공구한 정산 입금";

    private final SettlementRecordService recordService;
    private final SellerProfileRepository sellerProfileRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final SsafyTransferClient transferClient;
    private final SsafyApiLogService ssafyApiLogService;
    private final PolicyService policyService;
    private final Clock clock;

    public SettlementTransferService(SettlementRecordService recordService,
                                     SellerProfileRepository sellerProfileRepository,
                                     AuditLogService auditLogService,
                                     UserRepository userRepository,
                                     SsafyTransferClient transferClient,
                                     SsafyApiLogService ssafyApiLogService,
                                     PolicyService policyService,
                                     Clock clock) {
        this.recordService = recordService;
        this.sellerProfileRepository = sellerProfileRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.transferClient = transferClient;
        this.ssafyApiLogService = ssafyApiLogService;
        this.policyService = policyService;
        this.clock = clock;
    }

    /**
     * 정산금을 판매자에게 이체합니다 (STL-04).
     *
     * <p>실행 전에 상태를 다시 확인합니다 — 목록을 조회한 뒤 실행을 누르기까지 사이에
     * 보류되거나 이미 이체됐을 수 있습니다.
     *
     * @return 처리된 정산. 실패해도 행은 남고 사유가 기록됩니다
     */
    /**
     * 관리자가 직접 실행합니다 (STL-04·07).
     *
     * <p>돈이 실제로 나가는 지점이라 <b>누가 눌렀는지</b>를 감사 로그에 남깁니다. 결과가
     * 성공이든 실패든 시도 자체를 기록합니다 - 실패한 시도도 나중에 "왜 이 시각에
     * 이체가 걸렸나" 를 되짚는 근거가 됩니다.
     *
     * <p>재시도 배치({@code SettlementTransferRetryScheduler})는 사람이 아니므로
     * {@link #execute(Long)} 를 직접 부르고 감사 로그를 남기지 않습니다.
     */
    public Settlement execute(Long adminId, Long settlementId) {
        Settlement result = execute(settlementId);
        auditLogService.record(adminId, AuditLogService.TARGET_SETTLEMENT, settlementId,
                AuditLogService.ACTION_TRANSFER,
                AuditLogService.values("transferStatus", Settlement.TRANSFER_PENDING),
                AuditLogService.values("transferStatus", result.getTransferStatus(),
                        "status", result.getStatus(),
                        "sellerId", result.getSellerId(),
                        "amount", result.getSettlementAmount()));
        return result;
    }

    /** 재시도 배치가 부르는 경로. 사람의 조작이 아니라 감사 로그를 남기지 않습니다. */
    public Settlement execute(Long settlementId) {
        Settlement settlement = recordService.loadExecutable(settlementId);

        String platformAccount = policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO);
        if (platformAccount == null || platformAccount.isBlank()) {
            return recordService.failTransfer(settlementId, "플랫폼 출금 계좌가 설정되지 않았습니다.");
        }

        SellerProfile seller = sellerProfileRepository.findById(settlement.getSellerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "판매자를 찾을 수 없습니다. sellerId=" + settlement.getSellerId()));
        String sellerAccount = seller.getSsafyAccountNo();
        if (sellerAccount == null || sellerAccount.isBlank()) {
            return recordService.failTransfer(settlementId,
                    "판매자 정산 계좌가 등록되지 않았습니다.");
        }
        if (!seller.isAccountVerified()) {
            // 1원 인증을 통과하지 않은 계좌로 보내면 엉뚱한 사람에게 갈 수 있다.
            return recordService.failTransfer(settlementId,
                    "판매자 정산 계좌가 인증되지 않았습니다.");
        }

        // seller_id 는 users.user_id 와 같다. 금융망 호출에는 회원 userKey 가 필요하다.
        User sellerUser = userRepository.findById(settlement.getSellerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "판매자 계정을 찾을 수 없습니다. sellerId=" + settlement.getSellerId()));
        if (sellerUser.getSsafyUserKey() == null || sellerUser.getSsafyUserKey().isBlank()) {
            return recordService.failTransfer(settlementId,
                    "판매자의 SSAFY 금융망 회원 연동이 완료되지 않았습니다.");
        }

        return transfer(settlement, platformAccount, sellerAccount,
                sellerUser.getSsafyUserKey());
    }

    private Settlement transfer(Settlement settlement, String platformAccount,
                                String sellerAccount, String sellerUserKey) {
        Long settlementId = settlement.getSettlementId();
        long amount = settlement.getSettlementAmount();

        // 시도마다 새로 채번한다. 같은 번호를 다시 쓰면 금융망이 중복 거래로 거부한다.
        String institutionTxNo = ssafyApiLogService.generateInstitutionTransactionUniqueNo(
                SsafyTransferClient.API_SERVICE_CODE);

        SsafyTransferClient.TransferResponse response;
        try {
            response = transferClient.transfer(institutionTxNo,
                    policyService.getString(PolicyKey.SSAFY_INSTITUTION_CODE),
                    policyService.getString(PolicyKey.SSAFY_FINTECH_APP_NO),
                    sellerUserKey, platformAccount, sellerAccount, amount,
                    WITHDRAWAL_SUMMARY, DEPOSIT_SUMMARY);
        } catch (BusinessException exception) {
            // 응답을 받지 못했다. 이체가 일어났는지 알 수 없으므로 자동 재시도에서 뺀다.
            recordLog(institutionTxNo, settlementId, platformAccount, sellerAccount, amount,
                    null, exception.getMessage(), null);
            return recordService.markTransferUnknown(settlementId,
                    "이체 응답을 받지 못했습니다. 금융망 거래내역 확인 필요: " + exception.getMessage());
        }

        recordLog(institutionTxNo, settlementId, platformAccount, sellerAccount, amount,
                response.header().responseCode(), response.header().responseMessage(), response);

        if (!response.isSuccess()) {
            return recordService.failTransfer(settlementId, response.header().responseMessage());
        }
        return recordService.completeTransfer(settlementId, institutionTxNo,
                withdrawalTxNo(response), depositTxNo(response),
                LocalDateTime.now(clock));
    }

    /**
     * 출금 거래고유번호.
     *
     * <p>응답의 {@code REC} 는 출금·입금 두 건입니다({@code transactionType} 1=출금,
     * 2=입금). 순서를 가정하지 않고 유형으로 골라냅니다 — 금융망이 순서를 바꿔 줘도
     * 엉뚱한 번호를 저장하지 않기 위해서입니다.
     */
    private Long withdrawalTxNo(SsafyTransferClient.TransferResponse response) {
        return findByType(response, "1");
    }

    /** 입금 거래고유번호. 판매자가 은행에서 대조할 수 있는 값입니다. */
    private Long depositTxNo(SsafyTransferClient.TransferResponse response) {
        return findByType(response, "2");
    }

    private Long findByType(SsafyTransferClient.TransferResponse response, String type) {
        if (response.rec() == null) {
            return null;
        }
        return response.rec().stream()
                .filter(record -> type.equals(record.transactionType()))
                .map(SsafyTransferClient.TransferResponse.Record::transactionUniqueNo)
                .filter(value -> value != null && !value.isBlank())
                .map(Long::valueOf)
                .findFirst()
                .orElse(null);
    }

    /** 성공·실패와 무관하게 남깁니다. 금융망 거래내역과 대조할 유일한 단서입니다. */
    private void recordLog(String institutionTxNo, Long settlementId, String withdrawalAccountNo,
                           String depositAccountNo, long amount,
                           String responseCode, String responseMessage, Object rawResponse) {
        ssafyApiLogService.record(SsafyTransferClient.API_SERVICE_CODE, institutionTxNo,
                TARGET_TYPE_SETTLEMENT, settlementId,
                Map.of("withdrawalAccountNo", mask(withdrawalAccountNo),
                        "depositAccountNo", mask(depositAccountNo),
                        "transactionBalance", String.valueOf(amount)),
                responseCode, responseMessage, rawResponse);
    }

    private String mask(String accountNo) {
        if (accountNo == null || accountNo.length() <= 4) {
            return "****";
        }
        return "*".repeat(accountNo.length() - 4) + accountNo.substring(accountNo.length() - 4);
    }

    /**
     * 보류 (STL-05).
     *
     * <p>판매자 지급을 늦추는 결정이라 사유와 함께 누가 눌렀는지 남깁니다.
     */
    public Settlement hold(Long adminId, Long settlementId, String reason) {
        // 보류는 PENDING 에서만 됩니다(Settlement.hold 의 requireStatus). 그래서 이전
        // 상태를 따로 읽지 않고 그 값으로 기록합니다.
        Settlement result = recordService.hold(settlementId, reason);
        auditLogService.record(adminId, AuditLogService.TARGET_SETTLEMENT, settlementId,
                AuditLogService.ACTION_HOLD,
                AuditLogService.values("status", Settlement.STATUS_PENDING),
                AuditLogService.values("status", result.getStatus(), "reason", reason == null ? "" : reason));
        return result;
    }

    /** 보류 해제 (STL-06). 다시 실행 대기 상태가 됩니다. */
    public Settlement releaseHold(Long adminId, Long settlementId) {
        // 보류 해제는 HOLD 에서만 됩니다.
        Settlement result = recordService.releaseHold(settlementId);
        auditLogService.record(adminId, AuditLogService.TARGET_SETTLEMENT, settlementId,
                AuditLogService.ACTION_RELEASE_HOLD,
                AuditLogService.values("status", Settlement.STATUS_HOLD),
                AuditLogService.values("status", result.getStatus()));
        return result;
    }
}
