package com.gongguhan.domain.refund.service;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.refund.entity.RefundMethod;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FailedRecruitmentRefundRecordService {
    private final ParticipationRepository participations;
    private final GroupBuyRepository groupBuys;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final RefundLedgerService ledger;

    public FailedRecruitmentRefundRecordService(ParticipationRepository participations, GroupBuyRepository groupBuys,
            PaymentRepository payments, RefundRepository refunds, RefundLedgerService ledger) {
        this.participations=participations;this.groupBuys=groupBuys;this.payments=payments;this.refunds=refunds;this.ledger=ledger;
    }

    /** 금융 호출 없이 참여 상태와 원장 예약만 커밋한다. */
    @Transactional
    public Long reserve(Long participationId) {
        Participation p=participations.findWithLockByParticipationId(participationId).orElse(null);
        if(p==null || !Participation.STATUS_PAID.equals(p.getStatus())) return null;
        GroupBuy group=groupBuys.findById(p.getGroupBuyId()).orElse(null);
        Payment payment=payments.findByParticipationId(participationId).orElse(null);
        if(group==null || !GroupBuy.STATUS_FAILED.equals(group.getStatus()) || payment==null || !"PAID".equals(payment.getStatus())) return null;
        if(refunds.findActiveByParticipationId(participationId).isPresent()) return null;
        RefundMethod method=switch(payment.getMethod()) {
            case "CARD" -> RefundMethod.ACCOUNT_TRANSFER;
            case "KAKAOPAY" -> RefundMethod.KAKAOPAY;
            case "CRYPTO" -> RefundMethod.CRYPTO;
            default -> throw new IllegalStateException("지원하지 않는 환불 수단");
        };
        var opened=ledger.openForParticipation(participationId,p.getTotalAmount(),RefundReason.RECRUIT_FAILED,method);
        p.markRefunding();
        return opened.created()?opened.refund().getRefundId():null;
    }
}
