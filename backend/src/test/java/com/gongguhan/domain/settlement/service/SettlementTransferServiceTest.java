package com.gongguhan.domain.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.entity.SellerProfile;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 정산 계좌이체 실행 검증 (STL-04·07).
 *
 * <p><b>이 테스트의 중심은 "판매자에게 두 번 지급되지 않는다" 입니다.</b> 이체는
 * 되돌릴 수 없어서, 보내야 할 때 못 보내는 것보다 <b>보내면 안 될 때 보내는 쪽</b>이
 * 훨씬 큰 사고입니다. 그래서 실행을 막는 경로를 더 촘촘히 봅니다.
 */
class SettlementTransferServiceTest {

    private static final Long SETTLEMENT_ID = 15001L;
    private static final Long SELLER_ID = 201L;
    private static final long AMOUNT = 978_500L;
    private static final String PLATFORM_ACCOUNT = "0016174648358792";
    private static final String SELLER_ACCOUNT = "0204667768182760";
    private static final String USER_KEY = "2695628f-11a1-418e-b533-9ae19e0650ec";
    private static final String INSTITUTION_TX_NO = "20260914103500123456";

    private static final Instant NOW = Instant.parse("2026-09-14T05:00:00Z");

    private SettlementRecordService recordService;
    private SellerProfileRepository sellerProfileRepository;
    private UserRepository userRepository;
    private SsafyTransferClient transferClient;
    private SsafyApiLogService ssafyApiLogService;
    private PolicyService policyService;

    private SettlementTransferService service;

    @BeforeEach
    void setUp() {
        recordService = mock(SettlementRecordService.class);
        sellerProfileRepository = mock(SellerProfileRepository.class);
        userRepository = mock(UserRepository.class);
        transferClient = mock(SsafyTransferClient.class);
        ssafyApiLogService = mock(SsafyApiLogService.class);
        policyService = mock(PolicyService.class);

        when(ssafyApiLogService.generateInstitutionTransactionUniqueNo(anyString()))
                .thenReturn(INSTITUTION_TX_NO);

        service = new SettlementTransferService(recordService, sellerProfileRepository,
                userRepository, transferClient, ssafyApiLogService, policyService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------
    // 고정 객체
    // ------------------------------------------------------------------

    private Settlement executable() {
        Settlement settlement = mock(Settlement.class);
        when(settlement.getSettlementId()).thenReturn(SETTLEMENT_ID);
        when(settlement.getSellerId()).thenReturn(SELLER_ID);
        when(settlement.getSettlementAmount()).thenReturn(AMOUNT);
        return settlement;
    }

    private SellerProfile seller(String accountNo, boolean verified) {
        SellerProfile profile = mock(SellerProfile.class);
        when(profile.getSsafyAccountNo()).thenReturn(accountNo);
        when(profile.isAccountVerified()).thenReturn(verified);
        return profile;
    }

    private User sellerUser(String userKey) {
        User user = mock(User.class);
        when(user.getSsafyUserKey()).thenReturn(userKey);
        return user;
    }

    /**
     * 이체가 가능한 상태로 협력 객체를 세웁니다.
     *
     * <p>스텁 객체를 {@code when(...)} 호출 <b>밖에서</b> 먼저 완성합니다. 인자 안에서
     * 다른 mock 을 스터빙하면 {@code UnfinishedStubbingException} 이 납니다.
     */
    private void givenTransferable() {
        Settlement settlement = executable();
        SellerProfile profile = seller(SELLER_ACCOUNT, true);
        User user = sellerUser(USER_KEY);

        when(recordService.loadExecutable(SETTLEMENT_ID)).thenReturn(settlement);
        when(sellerProfileRepository.findById(SELLER_ID)).thenReturn(Optional.of(profile));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(user));
        when(policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO))
                .thenReturn(PLATFORM_ACCOUNT);
        when(policyService.getString(PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policyService.getString(PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
    }

    /** 출금(1)·입금(2) 두 건이 담긴 정상 응답. 순서를 일부러 뒤집어 둡니다. */
    private SsafyTransferClient.TransferResponse response(String code) {
        return new SsafyTransferClient.TransferResponse(
                new SsafyTransferClient.TransferResponse.Header(code, "메시지", INSTITUTION_TX_NO),
                List.of(new SsafyTransferClient.TransferResponse.Record(
                                "77", "20260914", "2", SELLER_ACCOUNT),
                        new SsafyTransferClient.TransferResponse.Record(
                                "66", "20260914", "1", PLATFORM_ACCOUNT)));
    }

    private void stubTransfer(SsafyTransferClient.TransferResponse result) {
        when(transferClient.transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(result);
    }

    private void verifyNoTransfer() {
        verify(transferClient, never()).transfer(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    // ------------------------------------------------------------------
    // 정상 이체
    // ------------------------------------------------------------------

    @Test
    @DisplayName("플랫폼 계좌에서 판매자 계좌로 정산금을 이체한다")
    void transfersToSeller() {
        givenTransferable();
        stubTransfer(response("H0000"));

        service.execute(SETTLEMENT_ID);

        verify(transferClient).transfer(INSTITUTION_TX_NO, "00100", "001", USER_KEY,
                PLATFORM_ACCOUNT, SELLER_ACCOUNT, AMOUNT, "공구한 정산 출금", "공구한 정산 입금");
    }

    /**
     * 응답의 {@code REC} 순서를 금융망이 바꿔 줘도 엉뚱한 번호를 저장하면 안 됩니다.
     * 고정 응답은 입금(2)이 먼저 오도록 뒤집어 두었습니다.
     */
    @Test
    @DisplayName("출금·입금 거래번호를 순서가 아니라 유형으로 골라낸다")
    void picksTransactionNumbersByType() {
        givenTransferable();
        stubTransfer(response("H0000"));

        service.execute(SETTLEMENT_ID);

        // 출금(type 1) = 66, 입금(type 2) = 77
        verify(recordService).completeTransfer(SETTLEMENT_ID, INSTITUTION_TX_NO, 66L, 77L,
                java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("이체 호출은 성공·실패 모두 감사 로그에 남는다")
    void logsEveryAttempt() {
        givenTransferable();
        stubTransfer(response("A1003"));

        service.execute(SETTLEMENT_ID);

        verify(ssafyApiLogService).record(eq(SsafyTransferClient.API_SERVICE_CODE),
                eq(INSTITUTION_TX_NO), eq("SETTLEMENT"), eq(SETTLEMENT_ID), any(),
                eq("A1003"), anyString(), any());
    }

    // ------------------------------------------------------------------
    // 이중 이체 방지 — 가장 중요한 부분
    // ------------------------------------------------------------------

    /**
     * 요청은 나갔는데 응답을 못 받은 상태입니다. 이체가 <b>일어났을 수도</b> 있으므로
     * 실패로 기록하면 자동 재시도가 집어가 판매자에게 두 번 지급됩니다.
     */
    @Test
    @DisplayName("응답을 못 받으면 실패가 아니라 결과 불명으로 남긴다 - 자동 재시도 차단")
    void marksUnknownWhenResponseMissing() {
        givenTransferable();
        when(transferClient.transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "SSAFY 금융망 연동이 일시적으로 불가능합니다."));

        service.execute(SETTLEMENT_ID);

        verify(recordService).markTransferUnknown(eq(SETTLEMENT_ID), anyString());
        verify(recordService, never()).failTransfer(anyLong(), anyString());
        verify(recordService, never()).completeTransfer(anyLong(), anyString(), any(), any(), any());
    }

    /** 금융망이 명시적으로 거부한 경우입니다. 이체가 일어나지 않았으므로 재시도해도 안전합니다. */
    @Test
    @DisplayName("금융망이 거부하면 실패로 남겨 자동 재시도 대상이 된다")
    void marksFailedWhenRejected() {
        givenTransferable();
        stubTransfer(response("A1003"));

        service.execute(SETTLEMENT_ID);

        verify(recordService).failTransfer(eq(SETTLEMENT_ID), anyString());
        verify(recordService, never()).markTransferUnknown(anyLong(), anyString());
    }

    /** 같은 번호를 다시 쓰면 금융망이 H1007(중복 거래)로 거부합니다. */
    @Test
    @DisplayName("기관거래고유번호는 시도마다 새로 채번한다")
    void generatesNewInstitutionTxNoEachAttempt() {
        givenTransferable();
        stubTransfer(response("H0000"));

        service.execute(SETTLEMENT_ID);
        service.execute(SETTLEMENT_ID);

        verify(ssafyApiLogService, org.mockito.Mockito.times(2))
                .generateInstitutionTransactionUniqueNo(SsafyTransferClient.API_SERVICE_CODE);
    }

    // ------------------------------------------------------------------
    // 실행 전 차단
    // ------------------------------------------------------------------

    @Test
    @DisplayName("플랫폼 출금 계좌가 설정되지 않았으면 이체하지 않는다")
    void failsWhenPlatformAccountMissing() {
        Settlement settlement = executable();
        when(recordService.loadExecutable(SETTLEMENT_ID)).thenReturn(settlement);
        when(policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO)).thenReturn("");

        service.execute(SETTLEMENT_ID);

        verifyNoTransfer();
        verify(recordService).failTransfer(eq(SETTLEMENT_ID), anyString());
    }

    @Test
    @DisplayName("판매자 정산 계좌가 없으면 이체하지 않는다")
    void failsWhenSellerAccountMissing() {
        Settlement settlement = executable();
        SellerProfile profile = seller(null, true);
        when(recordService.loadExecutable(SETTLEMENT_ID)).thenReturn(settlement);
        when(sellerProfileRepository.findById(SELLER_ID)).thenReturn(Optional.of(profile));
        when(policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO))
                .thenReturn(PLATFORM_ACCOUNT);

        service.execute(SETTLEMENT_ID);

        verifyNoTransfer();
        verify(recordService).failTransfer(eq(SETTLEMENT_ID), anyString());
    }

    /** 1원 인증을 통과하지 않은 계좌로 보내면 엉뚱한 사람에게 갈 수 있습니다. */
    @Test
    @DisplayName("인증되지 않은 계좌로는 이체하지 않는다")
    void failsWhenAccountNotVerified() {
        Settlement settlement = executable();
        SellerProfile profile = seller(SELLER_ACCOUNT, false);
        when(recordService.loadExecutable(SETTLEMENT_ID)).thenReturn(settlement);
        when(sellerProfileRepository.findById(SELLER_ID)).thenReturn(Optional.of(profile));
        when(policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO))
                .thenReturn(PLATFORM_ACCOUNT);

        service.execute(SETTLEMENT_ID);

        verifyNoTransfer();
        verify(recordService).failTransfer(eq(SETTLEMENT_ID), anyString());
    }

    @Test
    @DisplayName("판매자의 금융망 회원 연동이 안 됐으면 이체하지 않는다")
    void failsWhenSellerNotLinked() {
        Settlement settlement = executable();
        SellerProfile profile = seller(SELLER_ACCOUNT, true);
        User user = sellerUser(null);
        when(recordService.loadExecutable(SETTLEMENT_ID)).thenReturn(settlement);
        when(sellerProfileRepository.findById(SELLER_ID)).thenReturn(Optional.of(profile));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(user));
        when(policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO))
                .thenReturn(PLATFORM_ACCOUNT);

        service.execute(SETTLEMENT_ID);

        verifyNoTransfer();
        verify(recordService).failTransfer(eq(SETTLEMENT_ID), anyString());
    }

    /**
     * 보류·완료 건은 {@code loadExecutable} 이 막습니다. 목록을 조회한 뒤 버튼을
     * 누르기까지 사이에 상태가 바뀔 수 있어, 실행 직전에 다시 확인합니다.
     */
    @Test
    @DisplayName("실행할 수 없는 상태면 이체 시도조차 하지 않는다")
    void doesNotTransferWhenNotExecutable() {
        when(recordService.loadExecutable(SETTLEMENT_ID))
                .thenThrow(new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                        "실행할 수 없는 정산입니다."));

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.execute(SETTLEMENT_ID));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
        verifyNoTransfer();
    }

    // ------------------------------------------------------------------
    // 보류
    // ------------------------------------------------------------------

    @Test
    @DisplayName("보류와 보류 해제는 기록 서비스에 위임한다")
    void delegatesHoldOperations() {
        service.hold(SETTLEMENT_ID, "반품 분쟁");
        service.releaseHold(SETTLEMENT_ID);

        verify(recordService).hold(SETTLEMENT_ID, "반품 분쟁");
        verify(recordService).releaseHold(SETTLEMENT_ID);
    }

    /** 금액 계산이 바뀌지 않았음을 고정합니다. 명세서 12장 예시 값입니다. */
    @Test
    @DisplayName("이체 금액은 정산의 지급액을 그대로 쓴다")
    void transfersExactSettlementAmount() {
        givenTransferable();
        stubTransfer(response("H0000"));

        service.execute(SETTLEMENT_ID);

        verify(transferClient).transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(AMOUNT), anyString(), anyString());
    }
}
