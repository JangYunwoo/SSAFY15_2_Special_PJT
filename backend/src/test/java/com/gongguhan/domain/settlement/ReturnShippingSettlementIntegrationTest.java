package com.gongguhan.domain.settlement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.refund.service.SsafyTransferClient;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.CreateCommand;
import com.gongguhan.domain.returnrequest.service.ReturnRefundService;
import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository.SettlementTarget;
import com.gongguhan.domain.settlement.service.SettlementRecordService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.policy.PolicyService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:returnshipping;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class ReturnShippingSettlementIntegrationTest {
    @Autowired ReturnRefundService refunds;
    @Autowired ReturnRequestRepository returns;
    @Autowired SettlementTargetRepository targets;
    @Autowired SettlementRecordService records;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean SsafyTransferClient bank;
    @MockitoBean SsafyApiLogService logs;
    @MockitoBean PolicyService policies;
    @MockitoSpyBean com.gongguhan.domain.participation.repository.ParticipationRepository participationLocks;
    @MockitoSpyBean com.gongguhan.domain.groupbuy.repository.GroupBuyRepository groupLocks;
    static final AtomicLong IDS = new AtomicLong(990_000);

    @BeforeEach void provider() {
        when(policies.getString(any())).thenReturn("QA-NOT-LIVE");
        when(logs.generateInstitutionTransactionUniqueNo(anyString())).thenReturn("QA-RETURN-SHIPPING");
        when(bank.transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new SsafyTransferClient.TransferResponse(
                        new SsafyTransferClient.TransferResponse.Header("H0000","QA","QA-RETURN-SHIPPING"),
                        List.of(new SsafyTransferClient.TransferResponse.Record("1","20260921","1","QA-ACCOUNT"))));
    }

    @Test void provedShippingIsSettledWithoutSalesFeeAndReturnHistoryIsPreserved() {
        Fixture f = fixture(10000,1000,2000,false);
        refunds.processAutomatic(f.returnId());
        Settlement s = settle(f.id());
        assertThat(s.getShareTotal()).isZero();
        assertThat(s.getShippingTotal()).isEqualTo(3000);
        assertThat(s.getPlatformFee()).isZero();
        assertThat(s.getSettlementAmount()).isEqualTo(3000);
        assertThat(status("participations","participation_id",f.id())).isEqualTo("REFUNDED");
        assertThat(status("escrows","participation_id",f.id())).isEqualTo("RELEASED");
        assertThat(candidates(f.id())).isEmpty();
        verify(bank,times(1)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),eq(8000L),anyString(),anyString());
    }

    @Test void zeroRefundCompletesWithoutCallingProviderAndSettlesOnlyRecoverableShipping() {
        Fixture f = fixture(1000,1000,2000,false);
        refunds.processAutomatic(f.returnId());
        refunds.processAutomatic(f.returnId());
        assertThat(jdbc.queryForObject("SELECT amount FROM refunds WHERE return_id=?",Long.class,f.returnId())).isZero();
        assertThat(status("return_requests","return_id",f.returnId())).isEqualTo("REFUNDED");
        Settlement s = settle(f.id());
        assertThat(s.getSettlementAmount()).isEqualTo(2000);
        assertThat(s.getPlatformFee()).isZero();
        assertThat(jdbc.queryForObject("SELECT refunded_amount FROM escrows WHERE participation_id=?",Long.class,f.id())).isZero();
        verifyNoInteractions(bank);
    }

    @Test void fullDefectRefundHasNoShippingRemainder() {
        Fixture f = fixture(10000,1000,0,true);
        refunds.processAutomatic(f.returnId());
        assertThat(candidates(f.id())).isEmpty();
        assertThat(status("escrows","participation_id",f.id())).isEqualTo("REFUNDED");
    }

    @Test void settlementUsesParticipationThenGroupLockOrderLikeParticipationCancellation() {
        Fixture f = fixture(10000,1000,2000,false);
        refunds.processAutomatic(f.returnId());
        clearInvocations(participationLocks, groupLocks);
        settle(f.id());
        var order = inOrder(participationLocks, groupLocks);
        order.verify(participationLocks).findWithLockByParticipationId(f.id());
        order.verify(groupLocks).findWithLockByGroupBuyId(f.id());
    }

    @ParameterizedTest
    @ValueSource(strings={"escrow", "refund", "seller", "review", "hold"})
    void incompleteProofNeverBecomesShippingIncome(String corruption) {
        Fixture f = fixture(10000,1000,2000,false);
        refunds.processAutomatic(f.returnId());
        switch(corruption) {
            case "escrow" -> jdbc.update("UPDATE escrows SET refunded_amount=7999 WHERE participation_id=?",f.id());
            case "refund" -> jdbc.update("UPDATE refunds SET status='FAILED' WHERE return_id=?",f.returnId());
            case "seller" -> jdbc.update("UPDATE return_requests SET fee_bearer='SELLER' WHERE return_id=?",f.returnId());
            case "review" -> jdbc.update("UPDATE return_requests SET admin_review_required=TRUE WHERE return_id=?",f.returnId());
            case "hold" -> jdbc.update("UPDATE participations SET confirm_hold=TRUE WHERE participation_id=?",f.id());
            default -> throw new IllegalArgumentException(corruption);
        }
        assertThat(candidates(f.id())).isEmpty();
        assertThat(status("escrows","participation_id",f.id())).isEqualTo("HELD");
    }

    @Test void staleCandidateIsRejectedBeforeEscrowRelease() {
        Fixture f = fixture(10000,1000,2000,false);
        refunds.processAutomatic(f.returnId());
        List<SettlementTarget> selected = candidates(f.id());
        assertThat(selected).hasSize(1);
        jdbc.update("UPDATE participations SET confirm_hold=TRUE WHERE participation_id=?",f.id());
        assertThatThrownBy(() -> records.settle(f.id(), selected,now())).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_items WHERE participation_id=?",Integer.class,f.id())).isZero();
        assertThat(status("escrows","participation_id",f.id())).isEqualTo("HELD");
    }

    @Test void shippingOnlyRoundPreservesRoundingForFirstProductRoundAndOnlyOnce() {
        Fixture f = fixture(10000,1000,2000,false);
        refunds.processAutomatic(f.returnId());
        jdbc.update("UPDATE group_buys SET rounding_diff=7 WHERE group_buy_id=?",f.id());
        assertThat(settle(f.id()).getRoundingDiff()).isZero();
        confirmed(f.id());
        Settlement second = settle(f.id());
        assertThat(second.getRoundNo()).isEqualTo(2);
        assertThat(second.getRoundingDiff()).isEqualTo(7);
        confirmed(f.id());
        assertThat(settle(f.id()).getRoundingDiff()).isZero();
    }

    @Test void mixedRoundChargesSalesFeeOnProductShareOnly() {
        Fixture f = fixture(10000,1000,2000,false);
        refunds.processAutomatic(f.returnId());
        confirmed(f.id());
        Settlement s = settle(f.id());
        assertThat(s.getShareTotal()).isEqualTo(10000);
        assertThat(s.getShippingTotal()).isEqualTo(4000);
        assertThat(s.getPlatformFee()).isEqualTo(500);
        assertThat(s.getSettlementAmount()).isEqualTo(13500);
    }

    @Test void disjointParticipantsStillSerializeRoundAndRoundingAllocation() throws Exception {
        Fixture f = fixture(10000,1000,2000,false);
        refunds.processAutomatic(f.returnId());
        settle(f.id());
        jdbc.update("UPDATE group_buys SET rounding_diff=7 WHERE group_buy_id=?",f.id());
        confirmed(f.id());
        confirmed(f.id());
        List<SettlementTarget> selected = candidates(f.id());
        assertThat(selected).hasSize(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Settlement> a=executor.submit(() -> {start.await();return records.settle(f.id(),List.of(selected.get(0)),now());});
            Future<Settlement> b=executor.submit(() -> {start.await();return records.settle(f.id(),List.of(selected.get(1)),now());});
            start.countDown();
            Settlement left=a.get(15,TimeUnit.SECONDS),right=b.get(15,TimeUnit.SECONDS);
            assertThat(List.of(left.getRoundNo(),right.getRoundNo())).containsExactlyInAnyOrder(2,3);
            assertThat(left.getRoundingDiff()+right.getRoundingDiff()).isEqualTo(7);
        } finally { executor.shutdownNow(); }
    }

    private List<SettlementTarget> candidates(long groupId) {
        return targets.findTargets(now()).stream().filter(t -> t.groupBuyId().equals(groupId)).toList();
    }
    private Settlement settle(long groupId) { return records.settle(groupId,candidates(groupId),now()); }
    private LocalDateTime now() {return LocalDateTime.now(ZoneOffset.UTC);}
    private String status(String table,String key,long id) { return jdbc.queryForObject("SELECT status FROM "+table+" WHERE "+key+"=?",String.class,id); }
    private long confirmed(long groupId) {
        long id=IDS.incrementAndGet();
        jdbc.update("INSERT INTO participations(participation_id,group_buy_id,user_id,share_amount,shipping_fee,total_amount,recipient_name,recipient_phone,zipcode,address1,status,joined_at,settlement_eligible_at,confirm_notice_agreed,confirm_hold) VALUES(?,?,?,10000,1000,11000,'QA','01000000000','00000','QA','CONFIRMED',?,?,FALSE,FALSE)",id,groupId,groupId,now().minusDays(15),now().minusDays(1));
        jdbc.update("INSERT INTO escrows(participation_id,group_buy_id,amount,refunded_amount,status,held_at) VALUES(?,?,11000,0,'HELD',NOW())",id,groupId);
        return id;
    }
    private Fixture fixture(long share,long shipping,long returnFee,boolean defect) {
        long id=IDS.incrementAndGet();
        long total=share+shipping;
        LocalDateTime then=now().minusDays(15);
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,ssafy_user_key,ssafy_account_no,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,TRUE,'ACTIVE','QA-KEY','QA-ACCOUNT',NOW(),NOW())",id,UUID.randomUUID()+"@example.test");
        jdbc.update("INSERT INTO categories(category_id,name,depth,sort_order) VALUES(?,'QA',1,1)",id);
        jdbc.update("INSERT INTO products(product_id,seller_id,category_id,name,price,ai_extracted,withdrawal_restricted,restriction_review_status,status,created_at,updated_at) VALUES(?,?,?,'QA',?,FALSE,FALSE,'NONE','ACTIVE',NOW(),NOW())",id,id,id,share);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,platform_fee_rate,created_at,updated_at) VALUES(?,?,?,2,?,?,2,0,?,'FULFILLED',0,0.05,NOW(),NOW())",id,id,id,share,shipping,then);
        jdbc.update("INSERT INTO participations(participation_id,group_buy_id,user_id,share_amount,shipping_fee,total_amount,recipient_name,recipient_phone,zipcode,address1,status,joined_at,settlement_eligible_at,confirm_notice_agreed,confirm_hold,active_marker) VALUES(?,?,?,?,?,?,'QA','01000000000','00000','QA','DELIVERED',?,?,FALSE,TRUE,'Y')",id,id,id,share,shipping,total,then,then);
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,participation_id,method,krw_amount,slot_expires_at,idempotency_key,status,created_at,updated_at) VALUES(?,?,?,?,'CARD',?,?,?,'PAID',NOW(),NOW())",id,id,id,id,total,then,UUID.randomUUID().toString());
        jdbc.update("INSERT INTO escrows(participation_id,group_buy_id,amount,refunded_amount,status,held_at) VALUES(?,?,?,0,'HELD',NOW())",id,id,total);
        long returnId=new TransactionTemplate(transactions).execute(tx -> {
            String feeBearer=defect?"SELLER":"BUYER";
            long created=returns.create(new CreateCommand(id,defect?"DEFECT":"CHANGE_OF_MIND","QA",returnFee,feeBearer,defect?total:Math.max(0,share-returnFee),false,false,then,then,"00000","QA",null));
            returns.approve(created,feeBearer,then);
            return created;
        });
        return new Fixture(id,returnId);
    }
    record Fixture(long id,long returnId) {}
}
