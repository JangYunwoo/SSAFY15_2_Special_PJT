package com.gongguhan.domain.delivery.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.delivery.entity.Delivery;
import com.gongguhan.domain.delivery.repository.DeliveryRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.repository.SellerPenaltyRepository;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShippingViolationServiceTest {

    private static final Long SELLER_ID = 10L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final Long PARTICIPATION_ID = 300L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 10, 0, 0);

    private GroupBuyRepository groupBuyRepository;
    private ParticipationRepository participationRepository;
    private DeliveryRepository deliveryRepository;
    private SellerPenaltyRepository sellerPenaltyRepository;
    private PolicyService policyService;
    private ShippingViolationService shippingViolationService;

    @BeforeEach
    void setUp() {
        groupBuyRepository = mock(GroupBuyRepository.class);
        participationRepository = mock(ParticipationRepository.class);
        deliveryRepository = mock(DeliveryRepository.class);
        sellerPenaltyRepository = mock(SellerPenaltyRepository.class);
        policyService = mock(PolicyService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-10T00:00:00Z"), ZoneOffset.UTC);
        shippingViolationService = new ShippingViolationService(
                groupBuyRepository, participationRepository, deliveryRepository,
                sellerPenaltyRepository, policyService, clock);
        when(policyService.getInt(PolicyKey.FAKE_INVOICE_DETECT_DAYS)).thenReturn(7);
        when(deliveryRepository.findByStatusAndShippedAtBefore(any(), any())).thenReturn(List.of());
        when(groupBuyRepository.findByStatusAndShippingDeadlineAtBefore(any(), any())).thenReturn(List.of());
    }

    private GroupBuy overdueGroupBuy() {
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(groupBuy.getSellerId()).thenReturn(SELLER_ID);
        when(groupBuy.getShippingDeadlineAt()).thenReturn(NOW.minusDays(1));
        return groupBuy;
    }

    private Participation participation() {
        Participation participation = mock(Participation.class);
        when(participation.getParticipationId()).thenReturn(PARTICIPATION_ID);
        when(participation.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        return participation;
    }

    @Test
    void registersNoInvoiceCandidateWhenInvoiceMissingPastDeadline() {
        GroupBuy overdue = overdueGroupBuy();
        Participation row = participation();
        when(groupBuyRepository.findByStatusAndShippingDeadlineAtBefore(GroupBuy.STATUS_FULFILLED, NOW))
                .thenReturn(List.of(overdue));
        when(participationRepository.findByGroupBuyIdOrderByParticipationIdAsc(GROUP_BUY_ID))
                .thenReturn(List.of(row));
        when(deliveryRepository.findByParticipationIdIn(List.of(PARTICIPATION_ID)))
                .thenReturn(List.of(Delivery.ready(PARTICIPATION_ID)));

        shippingViolationService.detectViolations();

        ArgumentCaptor<SellerPenalty> penaltyCaptor = ArgumentCaptor.forClass(SellerPenalty.class);
        verify(sellerPenaltyRepository).save(penaltyCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                SellerPenalty.REASON_NO_INVOICE, penaltyCaptor.getValue().getReasonType());
    }

    @Test
    void skipsNoInvoiceWhenAllInvoicesRegistered() {
        GroupBuy overdue = overdueGroupBuy();
        Participation row = participation();
        when(groupBuyRepository.findByStatusAndShippingDeadlineAtBefore(GroupBuy.STATUS_FULFILLED, NOW))
                .thenReturn(List.of(overdue));
        when(participationRepository.findByGroupBuyIdOrderByParticipationIdAsc(GROUP_BUY_ID))
                .thenReturn(List.of(row));
        Delivery withInvoice = Delivery.ready(PARTICIPATION_ID);
        withInvoice.registerInvoice("CJ", "123456789012", NOW.minusDays(2));
        when(deliveryRepository.findByParticipationIdIn(List.of(PARTICIPATION_ID)))
                .thenReturn(List.of(withInvoice));

        shippingViolationService.detectViolations();

        verify(sellerPenaltyRepository, never()).save(any());
    }

    @Test
    void skipsDuplicateNoInvoiceCandidate() {
        GroupBuy overdue = overdueGroupBuy();
        when(groupBuyRepository.findByStatusAndShippingDeadlineAtBefore(GroupBuy.STATUS_FULFILLED, NOW))
                .thenReturn(List.of(overdue));
        when(sellerPenaltyRepository.existsByGroupBuyIdAndReasonType(
                GROUP_BUY_ID, SellerPenalty.REASON_NO_INVOICE)).thenReturn(true);

        shippingViolationService.detectViolations();

        verify(sellerPenaltyRepository, never()).save(any());
    }

    @Test
    void registersFakeInvoiceCandidateForStalledShipping() {
        Delivery stalled = Delivery.ready(PARTICIPATION_ID);
        stalled.registerInvoice("CJ", "123456789012", NOW.minusDays(10));
        stalled.markShipping();
        when(deliveryRepository.findByStatusAndShippedAtBefore(Delivery.STATUS_SHIPPING, NOW.minusDays(7)))
                .thenReturn(List.of(stalled));
        Participation row = participation();
        GroupBuy overdue = overdueGroupBuy();
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(row));
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(overdue));

        shippingViolationService.detectViolations();

        ArgumentCaptor<SellerPenalty> penaltyCaptor = ArgumentCaptor.forClass(SellerPenalty.class);
        verify(sellerPenaltyRepository).save(penaltyCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                SellerPenalty.REASON_FAKE_INVOICE, penaltyCaptor.getValue().getReasonType());
        verify(sellerPenaltyRepository).existsByGroupBuyIdAndReasonType(
                eq(GROUP_BUY_ID), eq(SellerPenalty.REASON_FAKE_INVOICE));
    }
}
