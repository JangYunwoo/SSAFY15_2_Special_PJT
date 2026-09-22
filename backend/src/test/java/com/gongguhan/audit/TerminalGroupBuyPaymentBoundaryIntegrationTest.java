package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.card.dto.CardApproveRequest;
import com.gongguhan.domain.payment.card.service.*;
import com.gongguhan.domain.payment.kakaopay.dto.KakaoPayApproveRequest;
import com.gongguhan.domain.payment.kakaopay.service.*;
import com.gongguhan.domain.payment.crypto.dto.*;
import com.gongguhan.domain.payment.crypto.service.*;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.policy.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:terminalboundary;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false", "app.kakaopay.secret-key=terminal-test-only-not-a-real-provider-key"})
@ActiveProfiles("test")
class TerminalGroupBuyPaymentBoundaryIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired CardPaymentService cards;
    @Autowired KakaoPayService kakao;
    @Autowired KakaoPayRecordService kakaoRecords;
    @Autowired KakaoPayStateSigner signer;
    @Autowired CryptoQuoteService quotes;
    @Autowired CryptoWalletProofService proofs;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean GroupBuySlotGate gate;
    @MockitoBean SsafyCardApiClient bank;
    @MockitoBean KakaoPayGateway gateway;
    @MockitoBean ExchangeRateProvider rates;
    @MockitoBean PolicyService policy;
    static final AtomicLong IDS=new AtomicLong(990000);
    record Fixture(long id,long payment,String method) {}

    @BeforeEach void gatewaysCannotUseNetwork() {
        when(gate.tryReserve(anyLong(),anyInt(),anyInt())).thenReturn(true);
        when(policy.getLong(PolicyKey.SSAFY_MERCHANT_ID)).thenReturn(1L);
        when(policy.getString(PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policy.getString(PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
        when(bank.createCreditCardTransaction(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong()))
                .thenThrow(new IllegalStateException("MOCK_EXTERNAL_BOUNDARY_REACHED"));
        when(gateway.ready(any())).thenThrow(new IllegalStateException("MOCK_EXTERNAL_BOUNDARY_REACHED"));
        when(gateway.approve(any())).thenThrow(new IllegalStateException("MOCK_EXTERNAL_BOUNDARY_REACHED"));
        when(rates.getKrwRate(anyString())).thenThrow(new IllegalStateException("MOCK_EXTERNAL_BOUNDARY_REACHED"));
    }
    Fixture prepare(String method) {
        long id=IDS.incrementAndGet();LocalDateTime now=LocalDateTime.now(clock);
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,ssafy_user_key,created_at,updated_at) VALUES(?,?,'test-only','QA',FALSE,FALSE,'ACTIVE','mock-member',NOW(),NOW())",id,"terminal-"+id+"@example.test");
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) VALUES(?,501,10,5,1000,0,0,0,?,?,'RECRUITING',0,NOW(),NOW())",id,now.minusHours(1),now.plusDays(1));
        jdbc.update("INSERT INTO user_addresses(address_id,user_id,recipient_name,recipient_phone,zipcode,address1,address2,is_default,created_at,updated_at) VALUES(?,?,'QA','01000000000','00000','QA','QA',TRUE,NOW(),NOW())",id,id);
        long payment=Long.parseLong(payments.prepare(id,id,new PaymentPrepareRequest(method,id,"CRYPTO".equals(method)?id:null),UUID.randomUUID().toString()).paymentId());
        if ("CRYPTO".equals(method)) {
            jdbc.update("INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at) VALUES(?,?,?,'QA',?,6,5,TRUE,NOW())",id,"Q"+id,id,"0x"+"c".repeat(40));
            jdbc.update("INSERT INTO crypto_wallets(wallet_id,chain_id,address,purpose,active,created_at) VALUES(?,?,?,'RECEIVE',TRUE,NOW())",id,id,"0x"+"d".repeat(40));
            jdbc.update("INSERT INTO crypto_quotes(quote_id,asset_id,market_rate,buffer_rate,applied_rate,krw_amount,asset_amount,issued_at,expires_at,used) VALUES(?,?,1000,0,1000,1000,1,?,?,FALSE)",id,id,now,now.plusMinutes(3));
        }
        return new Fixture(id,payment,method);
    }
    Runnable entry(String entry,Fixture f) {
        return switch(entry) {
            case "CARD" -> ()->cards.approve(f.id,f.payment,new CardApproveRequest("1005518816096479","725"));
            case "KAKAO_READY" -> ()->kakao.ready(f.id,f.payment);
            case "KAKAO_APPROVE" -> {
                LocalDateTime expires=LocalDateTime.now(clock).plusMinutes(3);
                var claims=signer.issue(f.payment,f.id,expires);
                kakaoRecords.recordReady(f.payment,"T"+f.payment,"PAYMENT-"+f.payment,String.valueOf(f.id),1000,"https://pay.example.test",claims.nonce(),expires);
                var request=new KakaoPayApproveRequest(f.payment,"mock-token",signer.sign(claims));
                yield ()->kakao.approve(f.id,request);
            }
            case "CRYPTO_QUOTE" -> ()->quotes.issueQuote(f.id,f.payment,new CryptoQuoteRequest(f.id));
            default -> ()->proofs.issue(f.id,f.payment,new CryptoWalletChallengeRequest(f.id,"0x"+"a".repeat(40),"0x"+"b".repeat(64)));
        };
    }
    @ParameterizedTest
    @CsvSource({"FAILED,CARD","CANCELED,CARD","FULFILLED,CARD",
            "FAILED,KAKAO_READY","CANCELED,KAKAO_READY","FULFILLED,KAKAO_READY",
            "FAILED,KAKAO_APPROVE","CANCELED,KAKAO_APPROVE","FULFILLED,KAKAO_APPROVE",
            "FAILED,CRYPTO_QUOTE","CANCELED,CRYPTO_QUOTE","FULFILLED,CRYPTO_QUOTE",
            "FAILED,CRYPTO_PROOF","CANCELED,CRYPTO_PROOF","FULFILLED,CRYPTO_PROOF"})
    void terminalGroupCannotStartNewFinancialStep(String state,String entry) {
        Fixture f=prepare(entry.startsWith("KAKAO")?"KAKAOPAY":entry.startsWith("CRYPTO")?"CRYPTO":"CARD");
        Runnable action=entry(entry,f);
        // 종료 배치/관리자 취소 이후 결제 TTL 정리 전의 잔여 PROCESSING 상태를 재현한다.
        jdbc.update("UPDATE group_buys SET status=?,reserved_slots=0 WHERE group_buy_id=?",state,f.id);
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e->assertThat(e.getErrorCode().name()).isEqualTo("GROUP_BUY_NOT_RECRUITING"));
        verifyNoInteractions(bank,gateway,rates);
        assertThat(jdbc.queryForObject("SELECT card_approval_status FROM payments WHERE payment_id=?",String.class,f.payment)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crypto_wallet_challenges WHERE payment_id=?",Integer.class,f.payment)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?",String.class,f.payment)).isEqualTo("PROCESSING");
    }
    @ParameterizedTest @ValueSource(strings={"CARD","KAKAOPAY","CRYPTO"})
    void recruitingSlotRemainsUsableWithinItsTtlEvenAfterRecruitmentClockBoundary(String method) {
        Fixture f=prepare(method);
        assertThat(payments.getApprovalContext(f.id,f.payment,method).paymentId()).isEqualTo(f.payment);
        jdbc.update("UPDATE group_buys SET recruit_end_at=? WHERE group_buy_id=?",LocalDateTime.now(clock).minusSeconds(1),f.id);
        assertThat(payments.getApprovalContext(f.id,f.payment,method).paymentId()).isEqualTo(f.payment);
        verifyNoInteractions(bank,gateway,rates);
    }

    @ParameterizedTest @ValueSource(strings={"FAILED","CANCELED","FULFILLED"})
    void completedPaymentResponseRemainsRecoverableAfterGroupTerminates(String state) {
        Fixture f=prepare("KAKAOPAY");
        jdbc.update("UPDATE payments SET status='PAID',slot_marker=NULL WHERE payment_id=?",f.payment);
        jdbc.update("UPDATE group_buys SET status=?,reserved_slots=0 WHERE group_buy_id=?",state,f.id);
        assertThat(payments.getCompletedPayment(f.id,f.payment,"KAKAOPAY").status()).isEqualTo("PAID");
        verifyNoInteractions(bank,gateway,rates);
    }
}
