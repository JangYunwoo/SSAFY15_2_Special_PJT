package com.gongguhan.domain.penalty;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.event.GroupBuyWithdrawnEvent;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.repository.SellerPenaltyRepository;
import com.gongguhan.domain.penalty.service.WithdrawalPenaltyListener;
import com.gongguhan.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WithdrawalPenaltyListenerTest {
    private final GroupBuyRepository groups = mock(GroupBuyRepository.class);
    private final SellerPenaltyRepository penalties = mock(SellerPenaltyRepository.class);
    private final WithdrawalPenaltyListener listener = new WithdrawalPenaltyListener(groups, penalties);
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 21, 0, 0);
    private void group(String status, long seller) {
        GroupBuy group = mock(GroupBuy.class);
        when(group.getSellerId()).thenReturn(seller);
        when(group.getStatus()).thenReturn(status);
        when(groups.findWithLockByGroupBuyId(1L)).thenReturn(Optional.of(group));
    }
    @Test void persistsOnlyCandidateWithEventTimeAndReason() {
        group("CANCELED", 2L);
        listener.onWithdrawn(new GroupBuyWithdrawnEvent(1L, 2L, 1, " 재고 부족 ", now));
        var captured = ArgumentCaptor.forClass(SellerPenalty.class);
        verify(penalties).save(captured.capture());
        assertThat(captured.getValue().getStatus()).isEqualTo("CANDIDATE");
        assertThat(captured.getValue().getReasonType()).isEqualTo("WITHDRAWAL");
        assertThat(captured.getValue().getDetectedAt()).isEqualTo(now);
        assertThat(captured.getValue().getDetail()).isEqualTo("판매자 판매 철회: 재고 부족");
    }
    @Test void doesNotDuplicateExistingCandidateOrReviewedLedger() {
        group("CANCELED", 2L);
        when(penalties.existsByGroupBuyIdAndReasonType(1L, "WITHDRAWAL")).thenReturn(true);
        listener.onWithdrawn(new GroupBuyWithdrawnEvent(1L, 2L, 1, "replay", now));
        verify(penalties, never()).save(any());
    }
    @Test void rejectsUncancelledGroup() {
        group("RECRUITING", 2L);
        assertThatThrownBy(() -> listener.onWithdrawn(new GroupBuyWithdrawnEvent(1L, 2L, 1, "QA", now)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(penalties);
    }
    @Test void rejectsMismatchedSeller() {
        group("CANCELED", 3L);
        assertThatThrownBy(() -> listener.onWithdrawn(new GroupBuyWithdrawnEvent(1L, 2L, 1, "QA", now)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(penalties);
    }
}
