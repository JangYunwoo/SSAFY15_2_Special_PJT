package com.gongguhan.domain.returnrequest.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.returnrequest.event.ReturnNeglectEvent;
import com.gongguhan.domain.returnrequest.port.RefundPort;
import com.gongguhan.domain.returnrequest.port.RefundPort.RefundCommand;
import com.gongguhan.domain.returnrequest.port.RefundPort.RefundResult;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.RefundContext;
import com.gongguhan.domain.returnrequest.service.ReturnRefundRecordService.Preparation;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ReturnRefundServiceTest {

    private ReturnRefundRecordService recordService;
    private ReturnRequestService returnRequestService;
    private RefundPort port;
    private ApplicationEventPublisher events;
    private ReturnRefundService service;
    private Preparation preparation;

    @BeforeEach
    void setUp() {
        recordService = mock(ReturnRefundRecordService.class);
        returnRequestService = mock(ReturnRequestService.class);
        port = mock(RefundPort.class);
        events = mock(ApplicationEventPublisher.class);
        RefundContext context = new RefundContext(40L, 30L, 20L, 50L,
                "KAKAOPAY", 44_000, "APPROVED", LocalDateTime.of(2026, 9, 10, 5, 0));
        RefundCommand command = new RefundCommand("RETURN-40", 40L, 50L, "KAKAOPAY", 44_000);
        preparation = new Preparation(context, command, true);
        when(port.supports("KAKAOPAY")).thenReturn(true);
        service = new ReturnRefundService(recordService, returnRequestService, List.of(port), events,
                Clock.fixed(Instant.parse("2026-09-11T05:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void successfulPickupRefundCompletesReturn() {
        when(recordService.prepareSeller(20L, 40L)).thenReturn(preparation);
        when(port.refund(preparation.command())).thenReturn(RefundResult.succeeded("A-CANCEL"));

        service.pickupComplete(20L, 40L);

        verify(recordService).complete(40L, "A-CANCEL");
        verify(recordService, never()).fail(any(), any());
    }

    @Test
    void providerFailureLinksRefundFailedStatus() {
        when(recordService.prepareSeller(20L, 40L)).thenReturn(preparation);
        when(port.refund(preparation.command())).thenReturn(RefundResult.failed("잔액 부족"));

        service.pickupComplete(20L, 40L);

        verify(recordService).fail(40L, "잔액 부족");
        verify(recordService, never()).complete(any(), any());
    }

    @Test
    void providerExceptionIsRecordedBeforeItPropagates() {
        when(recordService.prepareSeller(20L, 40L)).thenReturn(preparation);
        when(port.refund(preparation.command()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));

        try {
            service.pickupComplete(20L, 40L);
        } catch (BusinessException ignored) {
            // 실패 상태 기록 검증이 목적이다.
        }

        verify(recordService).fail(40L, ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE.getDefaultMessage());
    }

    @Test
    void duplicatePreparationSkipsProviderCall() {
        when(recordService.prepareSeller(20L, 40L))
                .thenReturn(new Preparation(preparation.context(), null, false));

        service.pickupComplete(20L, 40L);

        verify(port, never()).refund(any());
    }

    @Test
    void automaticRefundPublishesNeglectViolation() {
        when(recordService.prepareAutomatic(40L)).thenReturn(preparation);
        when(port.refund(preparation.command())).thenReturn(RefundResult.succeeded("A-CANCEL"));

        service.processAutomatic(40L);

        verify(events).publishEvent(any(ReturnNeglectEvent.class));
        verify(recordService).complete(40L, "A-CANCEL");
    }
}
