package com.gongguhan.domain.groupbuy.service;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.refund.service.RefundLedgerService;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

class GroupBuyWithdrawalServiceTest {
    private final RefundOrchestrationService execution = mock(RefundOrchestrationService.class);
    private final GroupBuyWithdrawalService preparation = mock(GroupBuyWithdrawalService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<GroupBuyWithdrawalService> self = mock(ObjectProvider.class);
    private GroupBuyWithdrawalService service() {
        when(self.getObject()).thenReturn(preparation);
        when(preparation.prepareWithdrawal(1L, 2L, "QA")).thenReturn(List.of(
                new GroupBuyWithdrawalService.RefundTarget(3L, 1000, 30L),
                new GroupBuyWithdrawalService.RefundTarget(4L, 2000, 40L)));
        return new GroupBuyWithdrawalService(mock(GroupBuyRepository.class), mock(ParticipationRepository.class),
                mock(PaymentRepository.class), execution, mock(ApplicationEventPublisher.class), self,
                Clock.systemUTC(), mock(RefundLedgerService.class));
    }
    @Test void resumesEveryReservedIdWithoutCreatingAnotherRefundLedger() {
        service().withdraw(1L, 2L, "QA");
        var order = inOrder(preparation, execution);
        order.verify(preparation).prepareWithdrawal(1L, 2L, "QA");
        order.verify(execution).resumePersisted(30L);
        order.verify(execution).resumePersisted(40L);
        verifyNoMoreInteractions(execution);
    }
    @Test void unexpectedFirstFailureDoesNotPreventSecondReservedRefund() {
        when(execution.resumePersisted(30L)).thenThrow(new IllegalStateException("QA unavailable"));
        service().withdraw(1L, 2L, "QA");
        verify(execution).resumePersisted(30L);
        verify(execution).resumePersisted(40L);
        verifyNoMoreInteractions(execution);
    }

    @Test void retriesOnlySnapshotConflictAndExecutesOnlySuccessfulReservations() {
        var service = service();
        when(preparation.prepareWithdrawal(1L, 2L, "QA"))
                .thenThrow(new GroupBuyWithdrawalService.WithdrawalSnapshotChangedException())
                .thenReturn(List.of(new GroupBuyWithdrawalService.RefundTarget(3L, 1000, 30L)));
        service.withdraw(1L, 2L, "QA");
        verify(preparation, times(2)).prepareWithdrawal(1L, 2L, "QA");
        verify(execution).resumePersisted(30L);
        verifyNoMoreInteractions(execution);
    }

    @Test void retryIsBoundedAndNeverSendsWhenSnapshotKeepsChanging() {
        var service = service();
        when(preparation.prepareWithdrawal(1L, 2L, "QA"))
                .thenThrow(new GroupBuyWithdrawalService.WithdrawalSnapshotChangedException());
        assertThatThrownBy(() -> service.withdraw(1L, 2L, "QA")).isInstanceOf(com.gongguhan.global.error.BusinessException.class);
        verify(preparation, times(3)).prepareWithdrawal(1L, 2L, "QA");
        verifyNoInteractions(execution);
    }

    @Test void doesNotRetryUnrelatedPreparationFailure() {
        var service = service();
        when(preparation.prepareWithdrawal(1L, 2L, "QA")).thenThrow(new IllegalStateException("QA database failure"));
        assertThatThrownBy(() -> service.withdraw(1L, 2L, "QA")).isInstanceOf(IllegalStateException.class);
        verify(preparation).prepareWithdrawal(1L, 2L, "QA");
        verifyNoInteractions(execution);
    }
}
