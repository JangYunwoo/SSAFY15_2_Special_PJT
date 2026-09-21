package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.card.dto.CardApproveRequest;
import com.gongguhan.domain.payment.card.service.*;
import com.gongguhan.domain.payment.kakaopay.service.*;
import com.gongguhan.domain.payment.crypto.dto.*;
import com.gongguhan.domain.payment.crypto.service.*;
import com.gongguhan.domain.delivery.dto.SellerDeliveryItemResponse;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:shippingsnapshot;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "app.admin.seed-enabled=false",
        "app.kakaopay.secret-key=shipping-test-only-not-a-real-provider-key"})
@ActiveProfiles("test")
class PaymentShippingSnapshotIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired PaymentRepository repository;
    @Autowired ParticipationRepository participations;
    @Autowired CardPaymentService cards;
    @Autowired KakaoPayService kakao;
    @Autowired KakaoPayStateSigner signer;
    @Autowired KakaoPayRecordService kakaoRecords;
    @Autowired CryptoQuoteService quotes;
    @Autowired CryptoWalletProofService proofs;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean GroupBuySlotGate gate;
    @MockitoBean SsafyCardApiClient bank;
    @MockitoBean KakaoPayGateway gateway;
    @MockitoBean ExchangeRateProvider rates;
    @MockitoBean com.gongguhan.global.policy.PolicyService policy;
    static final AtomicLong IDS=new AtomicLong(970000);
    record Fixture(long id, long payment, String key, PaymentPrepareRequest request) {}
    Fixture fixture(String method, String request) {
        long id=IDS.incrementAndGet();
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) VALUES(?,501,10,5,1000,0,0,0,?,?,'RECRUITING',0,NOW(),NOW())",id,LocalDateTime.now(clock).minusHours(1),LocalDateTime.now(clock).plusDays(1));
        jdbc.update("INSERT INTO user_addresses(address_id,user_id,recipient_name,recipient_phone,zipcode,address1,address2,delivery_request,is_default,created_at,updated_at) VALUES(?,?,'수령인','01000000000','00000','주소','상세','기본 요청',TRUE,NOW(),NOW())",id,id);
        when(gate.tryReserve(anyLong(),anyInt(),anyInt())).thenReturn(true);
        String key=UUID.randomUUID().toString();
        var body=new PaymentPrepareRequest(method,id,"CRYPTO".equals(method)?7L:null,request);
        return new Fixture(id,Long.parseLong(payments.prepare(id,id,body,key).paymentId()),key,body);
    }
    void clearSnapshot(Fixture f) {
        jdbc.update("UPDATE payments SET shipping_recipient_name=NULL,shipping_recipient_phone=NULL,shipping_zipcode=NULL,shipping_address1=NULL,shipping_address2=NULL,shipping_delivery_request=NULL WHERE payment_id=?",f.payment);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"이번에는 경비실"})
    void requestIsPreservedThroughParticipationAndSellerResponse(String request) {
        Fixture f=fixture("CARD",request);
        jdbc.update("UPDATE user_addresses SET delivery_request='나중 기본값' WHERE address_id=?",f.id);
        assertThat(payments.prepare(f.id,f.id,f.request,f.key).paymentId()).isEqualTo(String.valueOf(f.payment));
        var paid=payments.completeOwnedPayment(f.id,f.payment,"CARD");
        var participation=participations.findById(Long.parseLong(paid.participationId())).orElseThrow();
        String expected=request==null?"기본 요청":request;
        assertThat(participation.getDeliveryRequest()).isEqualTo(expected);
        assertThat(SellerDeliveryItemResponse.of(participation,null).deliveryRequest()).isEqualTo(expected);
    }
    @Test void changedRequestCannotReuseSameOrActiveKey() {
        Fixture f=fixture("CARD",null);
        for(String key:new String[]{f.key,"other-"+f.key}) {
            assertThatThrownBy(()->payments.prepare(f.id,f.id,new PaymentPrepareRequest("CARD",f.id,null,""),key))
                    .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode().name()).isEqualTo("PAYMENT_REQUEST_CONFLICT"));
        }
    }
    @ParameterizedTest @ValueSource(strings={"CARD","KAKAOPAY","CRYPTO"})
    void legacyOwnedAddressIsCapturedOnceBeforeExternalBoundary(String method) {
        Fixture f=fixture(method,null); clearSnapshot(f);
        payments.getApprovalContext(f.id,f.payment,method);
        jdbc.update("DELETE FROM user_addresses WHERE address_id=?",f.id);
        payments.getApprovalContext(f.id,f.payment,method);
        assertThat(payments.completeOwnedPayment(f.id,f.payment,method).status()).isEqualTo("PAID");
    }
    @ParameterizedTest @ValueSource(strings={"CARD","KAKAOPAY","CRYPTO_QUOTE","CRYPTO_PROOF"})
    void missingLegacyAddressBlocksEveryExternalEntry(String entry) {
        Fixture f=fixture(entry.startsWith("CRYPTO")?"CRYPTO":entry,null); clearSnapshot(f);
        jdbc.update("DELETE FROM user_addresses WHERE address_id=?",f.id);
        Runnable action=switch(entry) {
            case "CARD" -> ()->cards.approve(f.id,f.payment,new CardApproveRequest("1005518816096479","725"));
            case "KAKAOPAY" -> ()->kakao.ready(f.id,f.payment);
            case "CRYPTO_QUOTE" -> ()->quotes.issueQuote(f.id,f.payment,new CryptoQuoteRequest(7L));
            default -> ()->proofs.issue(f.id,f.payment,new CryptoWalletChallengeRequest(7L,"0x"+"a".repeat(40),"0x"+"b".repeat(64)));
        };
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode().name()).isEqualTo("VALIDATION_ERROR"));
        verifyNoInteractions(bank,gateway,rates);
        assertThat(repository.findById(f.payment).orElseThrow().getCardApprovalStatus()).isNull();
    }
    @Test void foreignLegacyAddressCannotBeCapturedAndPostApprovalPathNeverRepairsIt() {
        Fixture f=fixture("CARD",null); clearSnapshot(f);
        jdbc.update("UPDATE user_addresses SET user_id=? WHERE address_id=?",f.id+1,f.id);
        assertThatThrownBy(()->payments.getApprovalContext(f.id,f.payment,"CARD")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->payments.completeOwnedPayment(f.id,f.payment,"CARD"))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode().name()).isEqualTo("INVALID_STATE_TRANSITION"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participations WHERE group_buy_id=?",Integer.class,f.id)).isZero();
    }
    @Test void kakaoApprovalAlsoRejectsMissingLegacyAddressBeforeCharge() {
        Fixture f=fixture("KAKAOPAY",null); clearSnapshot(f);
        jdbc.update("DELETE FROM user_addresses WHERE address_id=?",f.id);
        String state=signer.sign(signer.issue(f.payment,f.id,LocalDateTime.now(clock).plusMinutes(3)));
        assertThatThrownBy(()->kakao.approve(f.id,new com.gongguhan.domain.payment.kakaopay.dto.KakaoPayApproveRequest(f.payment,"mock-token",state)))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode().name()).isEqualTo("VALIDATION_ERROR"));
        verifyNoInteractions(gateway);
    }
    @Test void simultaneousLegacyCaptureAndDuplicateCompletionHaveOneOutcome() throws Exception {
        Fixture f=fixture("CARD",null); clearSnapshot(f);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            Callable<Void> capture=()->{start.await();payments.getApprovalContext(f.id,f.payment,"CARD");return null;};
            var a=pool.submit(capture);var b=pool.submit(capture);start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
            var completeStart=new CountDownLatch(1);
            Callable<Boolean> complete=()->{completeStart.await();try {payments.completeOwnedPayment(f.id,f.payment,"CARD");return true;}catch(BusinessException e){return false;}};
            var x=pool.submit(complete);var y=pool.submit(complete);completeStart.countDown();
            assertThat((x.get(10,TimeUnit.SECONDS)?1:0)+(y.get(10,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participations WHERE group_buy_id=?",Integer.class,f.id)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void cardApprovalCommitsOriginalShippingEvenWhenAddressIsDeletedDuringGatewayCall() {
        Fixture f=fixture("CARD","문 앞");
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,ssafy_user_key,created_at,updated_at) VALUES(?,?, 'test-only','QA',FALSE,FALSE,'ACTIVE','mock-member',NOW(),NOW())",f.id,"shipping-"+f.id+"@example.test");
        when(policy.getLong(com.gongguhan.global.policy.PolicyKey.SSAFY_MERCHANT_ID)).thenReturn(1L);
        when(policy.getString(com.gongguhan.global.policy.PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policy.getString(com.gongguhan.global.policy.PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
        when(bank.createCreditCardTransaction(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong()))
                .thenAnswer(invocation->{
                    jdbc.update("DELETE FROM user_addresses WHERE address_id=?",f.id);
                    return new SsafyCardApiClient.SsafyCardTransactionResponse(
                        new SsafyCardApiClient.SsafyCardTransactionResponse.Header("H0000","mock approved"),
                        new SsafyCardApiClient.SsafyCardTransactionResponse.Rec(String.valueOf(f.payment),"TEST","테스트","1","테스트","20260921","010000","1000"));
                });
        cards.approve(f.id,f.payment,new CardApproveRequest("1005518816096479","725"));
        Payment paid=repository.findById(f.payment).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo("PAID");
        assertThat(paid.getCardApprovalStatus()).isEqualTo("COMPLETED");
        assertThat(participations.findById(paid.getParticipationId()).orElseThrow().getDeliveryRequest()).isEqualTo("문 앞");
        verify(bank,times(1)).createCreditCardTransaction(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),eq(1000L));
    }
    @Test void kakaoApprovalCommitsSnapshotAfterAddressDeletionAndRetryNeverChargesAgain() {
        Fixture f=fixture("KAKAOPAY","경비실");
        LocalDateTime expires=LocalDateTime.now(clock).plusMinutes(3);
        var claims=signer.issue(f.payment,f.id,expires);
        kakaoRecords.recordReady(f.payment,"T"+f.payment,"PAYMENT-"+f.payment,String.valueOf(f.id),1000,"https://pay.example.test",claims.nonce(),expires);
        when(gateway.approve(any())).thenAnswer(invocation->{
            jdbc.update("DELETE FROM user_addresses WHERE address_id=?",f.id);
            return new KakaoPayGateway.ApproveResult("A"+f.payment,"T"+f.payment,"PAYMENT-"+f.payment,String.valueOf(f.id),1000,0,"MONEY");
        });
        var request=new com.gongguhan.domain.payment.kakaopay.dto.KakaoPayApproveRequest(f.payment,"mock-token",signer.sign(claims));
        var first=kakao.approve(f.id,request);
        var retry=kakao.approve(f.id,request);
        assertThat(retry.participationId()).isEqualTo(first.participationId());
        assertThat(participations.findById(Long.parseLong(first.participationId())).orElseThrow().getDeliveryRequest()).isEqualTo("경비실");
        verify(gateway,times(1)).approve(any());
    }
    @Test void quoteRateLookupHoldsNoTransactionAndAbortDuringLookupPreventsProof() {
        Fixture f=fixture("CRYPTO",null); clearSnapshot(f);
        jdbc.update("UPDATE payments SET prepare_crypto_asset_id=? WHERE payment_id=?",f.id,f.payment);
        jdbc.update("INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at) VALUES(?,'QA',?,'QA',?,6,5,TRUE,NOW())",f.id,f.id,"0x"+"c".repeat(40));
        jdbc.update("INSERT INTO crypto_wallets(wallet_id,chain_id,address,purpose,active,created_at) VALUES(?,?,?,'RECEIVE',TRUE,NOW())",f.id,f.id,"0x"+"d".repeat(40));
        when(policy.getRate(com.gongguhan.global.policy.PolicyKey.CRYPTO_BUFFER_RATE)).thenReturn(java.math.BigDecimal.ZERO);
        when(policy.getSeconds(com.gongguhan.global.policy.PolicyKey.QUOTE_TTL_SEC)).thenReturn(java.time.Duration.ofMinutes(2));
        when(rates.getKrwRate("QA")).thenAnswer(invocation->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(repository.findById(f.payment).orElseThrow().getShippingSnapshot()).isNotNull();
            payments.abort(f.id,f.payment);
            return java.math.BigDecimal.valueOf(1000);
        });
        var quote=quotes.issueQuote(f.id,f.payment,new CryptoQuoteRequest(f.id));
        assertThatThrownBy(()->proofs.issue(f.id,f.payment,new CryptoWalletChallengeRequest(Long.parseLong(quote.quoteId()),"0x"+"a".repeat(40),"0x"+"b".repeat(64))))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode().name()).isEqualTo("PAYMENT_ALREADY_PROCESSED"));
    }
    @Test void deliveryRequestLimitAndSnapshotImmutabilityAreEnforced() {
        Fixture f=fixture("CARD",null);
        assertThatThrownBy(()->payments.prepare(f.id,f.id,new PaymentPrepareRequest("CARD",f.id,null,"가".repeat(101)),f.key))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode().name()).isEqualTo("VALIDATION_ERROR"));
        Payment payment=repository.findById(f.payment).orElseThrow();
        assertThatThrownBy(()->payment.captureShipping(payment.getShippingSnapshot(),"변경")).isInstanceOf(IllegalStateException.class);
    }
}
