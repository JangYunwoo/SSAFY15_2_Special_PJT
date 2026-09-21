package com.gongguhan.domain.delivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.gongguhan.domain.delivery.dto.DeliveryBulkStatusResponse;
import com.gongguhan.domain.delivery.dto.DeliveryInvoiceRequest;
import com.gongguhan.domain.delivery.entity.Delivery;
import com.gongguhan.domain.delivery.repository.DeliveryRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
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

class DeliveryServiceTest {

    private static final Long SELLER_ID = 10L;
    private static final Long OTHER_SELLER_ID = 11L;
    private static final Long USER_ID = 1L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final Long DELIVERY_ID = 500L;
    private static final Long PARTICIPATION_ID = 300L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    private DeliveryRepository deliveryRepository;
    private ParticipationRepository participationRepository;
    private GroupBuyRepository groupBuyRepository;
    private PolicyService policyService;
    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryRepository.class);
        participationRepository = mock(ParticipationRepository.class);
        groupBuyRepository = mock(GroupBuyRepository.class);
        policyService = mock(PolicyService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        deliveryService = new DeliveryService(
                deliveryRepository, participationRepository, groupBuyRepository, policyService, clock);
    }

    private GroupBuy fulfilledGroupBuy(Long sellerId) {
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getSellerId()).thenReturn(sellerId);
        when(groupBuy.getStatus()).thenReturn(GroupBuy.STATUS_FULFILLED);
        return groupBuy;
    }

    private Participation participation(Long userId) {
        Participation participation = mock(Participation.class);
        when(participation.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(participation.getUserId()).thenReturn(userId);
        return participation;
    }

    private void stubDeliveryChain(Delivery delivery, Participation participation, GroupBuy groupBuy) {
        when(deliveryRepository.findById(DELIVERY_ID)).thenReturn(Optional.of(delivery));
        when(delivery.getParticipationId()).thenReturn(PARTICIPATION_ID);
        when(deliveryRepository.findParticipationIdByDeliveryId(DELIVERY_ID)).thenReturn(Optional.of(PARTICIPATION_ID));
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
    }

    private DeliveryInvoiceRequest invoiceRequest() {
        return new DeliveryInvoiceRequest("CJ", "123456789012", null);
    }

    @Test
    void registerInvoiceStoresInvoiceWithServerTimeWhenShippedAtOmitted() {
        Delivery delivery = mock(Delivery.class);
        stubDeliveryChain(delivery, participation(USER_ID), fulfilledGroupBuy(SELLER_ID));
        when(deliveryRepository.existsByCourierCodeAndTrackingNumberAndDeliveryIdNot(
                "CJ", "123456789012", DELIVERY_ID)).thenReturn(false);

        deliveryService.registerInvoice(SELLER_ID, DELIVERY_ID, invoiceRequest());

        verify(delivery).registerInvoice("CJ", "123456789012", NOW);
    }

    @Test
    void registerInvoiceDeniedForOtherSeller() {
        Delivery delivery = mock(Delivery.class);
        stubDeliveryChain(delivery, participation(USER_ID), fulfilledGroupBuy(SELLER_ID));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deliveryService.registerInvoice(OTHER_SELLER_ID, DELIVERY_ID, invoiceRequest()));

        assertEquals(ErrorCode.RESOURCE_ACCESS_DENIED, exception.getErrorCode());
        verify(delivery, never()).registerInvoice(anyString(), anyString(), any());
    }

    @Test
    void registerInvoiceRejectedWhenGroupBuyNotFulfilled() {
        Delivery delivery = mock(Delivery.class);
        GroupBuy recruiting = mock(GroupBuy.class);
        when(recruiting.getSellerId()).thenReturn(SELLER_ID);
        when(recruiting.getStatus()).thenReturn(GroupBuy.STATUS_RECRUITING);
        stubDeliveryChain(delivery, participation(USER_ID), recruiting);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deliveryService.registerInvoice(SELLER_ID, DELIVERY_ID, invoiceRequest()));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }

    @Test
    void registerInvoiceRejectedOnDuplicateTrackingNumber() {
        Delivery delivery = mock(Delivery.class);
        stubDeliveryChain(delivery, participation(USER_ID), fulfilledGroupBuy(SELLER_ID));
        when(deliveryRepository.existsByCourierCodeAndTrackingNumberAndDeliveryIdNot(
                "CJ", "123456789012", DELIVERY_ID)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deliveryService.registerInvoice(SELLER_ID, DELIVERY_ID, invoiceRequest()));

        assertEquals(ErrorCode.DUPLICATE_TRACKING_NUMBER, exception.getErrorCode());
        verify(delivery, never()).registerInvoice(anyString(), anyString(), any());
    }

    @Test
    void updateStatusShippingTransitionsDeliveryAndParticipation() {
        Delivery delivery = mock(Delivery.class);
        Participation participation = participation(USER_ID);
        stubDeliveryChain(delivery, participation, fulfilledGroupBuy(SELLER_ID));

        deliveryService.updateStatus(SELLER_ID, DELIVERY_ID, "SHIPPING");

        verify(delivery).markShipping();
        verify(participation).markShipping();
        var ordered = inOrder(deliveryRepository, participationRepository, delivery);
        ordered.verify(deliveryRepository).findParticipationIdByDeliveryId(DELIVERY_ID);
        ordered.verify(participationRepository).findWithLockByParticipationId(PARTICIPATION_ID);
        ordered.verify(deliveryRepository).findById(DELIVERY_ID);
        ordered.verify(delivery).markShipping();
        verify(participationRepository, never()).findById(any());
    }

    @Test
    void updateStatusDeliveredAppliesPolicyDeadlines() {
        Delivery delivery = mock(Delivery.class);
        Participation participation = participation(USER_ID);
        stubDeliveryChain(delivery, participation, fulfilledGroupBuy(SELLER_ID));
        when(policyService.getInt(PolicyKey.WITHDRAWAL_PERIOD_DAYS)).thenReturn(7);
        when(policyService.getInt(PolicyKey.SETTLEMENT_ELIGIBLE_DAYS)).thenReturn(7);

        deliveryService.updateStatus(SELLER_ID, DELIVERY_ID, "DELIVERED");

        verify(delivery).markDelivered(NOW);
        verify(participation).markDelivered(NOW, NOW.plusDays(7), NOW.plusDays(7));
    }

    @Test
    void updateStatusRejectsUnknownStatus() {
        Delivery delivery = mock(Delivery.class);
        stubDeliveryChain(delivery, participation(USER_ID), fulfilledGroupBuy(SELLER_ID));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deliveryService.updateStatus(SELLER_ID, DELIVERY_ID, "READY"));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void bulkUpdateSkipsIneligibleRows() {
        GroupBuy groupBuy = fulfilledGroupBuy(SELLER_ID);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));

        Participation eligible = mock(Participation.class);
        when(eligible.getParticipationId()).thenReturn(301L);
        when(eligible.getStatus()).thenReturn(Participation.STATUS_FULFILLED);
        Participation ineligible = mock(Participation.class);
        when(ineligible.getParticipationId()).thenReturn(302L);
        when(ineligible.getStatus()).thenReturn(Participation.STATUS_FULFILLED);
        when(participationRepository.findIdsByGroupBuyIdOrderByParticipationIdAsc(GROUP_BUY_ID))
                .thenReturn(List.of(301L, 302L));
        when(participationRepository.findWithLockByParticipationId(301L)).thenReturn(Optional.of(eligible));
        when(participationRepository.findWithLockByParticipationId(302L)).thenReturn(Optional.of(ineligible));

        Delivery withInvoice = mock(Delivery.class);
        when(withInvoice.getParticipationId()).thenReturn(301L);
        when(withInvoice.isReady()).thenReturn(true);
        when(withInvoice.hasInvoice()).thenReturn(true);
        Delivery withoutInvoice = mock(Delivery.class);
        when(withoutInvoice.getParticipationId()).thenReturn(302L);
        when(withoutInvoice.isReady()).thenReturn(true);
        when(withoutInvoice.hasInvoice()).thenReturn(false);
        when(deliveryRepository.findByParticipationId(301L)).thenReturn(Optional.of(withInvoice));
        when(deliveryRepository.findByParticipationId(302L)).thenReturn(Optional.of(withoutInvoice));

        DeliveryBulkStatusResponse response =
                deliveryService.bulkUpdateStatus(SELLER_ID, GROUP_BUY_ID, "SHIPPING");

        assertEquals(2, response.totalCount());
        assertEquals(1, response.updatedCount());
        assertEquals(1, response.skippedCount());
        verify(withInvoice).markShipping();
        verify(eligible).markShipping();
        verify(withoutInvoice, never()).markShipping();
        verify(ineligible, never()).markShipping();
        var ordered = inOrder(participationRepository, deliveryRepository);
        ordered.verify(participationRepository).findIdsByGroupBuyIdOrderByParticipationIdAsc(GROUP_BUY_ID);
        ordered.verify(participationRepository).findWithLockByParticipationId(301L);
        ordered.verify(deliveryRepository).findByParticipationId(301L);
        ordered.verify(participationRepository).findWithLockByParticipationId(302L);
        ordered.verify(deliveryRepository).findByParticipationId(302L);
        verify(participationRepository, never()).findByGroupBuyIdOrderByParticipationIdAsc(any());
    }

    @Test
    void getUserDeliveryDeniedForOtherUser() {
        Participation participation = participation(USER_ID);
        when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deliveryService.getUserDelivery(2L, PARTICIPATION_ID));

        assertEquals(ErrorCode.RESOURCE_ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void getUserDeliveryFailsWhenDeliveryNotProvisionedYet() {
        Participation participation = participation(USER_ID);
        when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
        when(deliveryRepository.findByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> deliveryService.getUserDelivery(USER_ID, PARTICIPATION_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }
}
