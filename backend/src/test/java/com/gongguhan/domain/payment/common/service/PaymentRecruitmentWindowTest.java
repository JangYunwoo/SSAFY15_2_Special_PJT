package com.gongguhan.domain.payment.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.port.ParticipationWriterPort;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentRecruitmentWindowTest {
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-21T06:00:00");
    private GroupBuyRepository groups;
    private PaymentRepository payments;
    private PaymentSlotReservationTransaction transaction;

    @BeforeEach void setup() {
        groups = mock(GroupBuyRepository.class);
        payments = mock(PaymentRepository.class);
        var addresses = mock(UserAddressRepository.class);
        var address = mock(UserAddress.class);
        when(address.getAddressId()).thenReturn(10L);
        when(addresses.findByAddressIdAndUserId(10L, 1L)).thenReturn(Optional.of(address));
        transaction = new PaymentSlotReservationTransaction(groups, mock(ParticipationWriterPort.class), payments,
                addresses, Clock.fixed(Instant.parse("2026-09-21T06:00:00Z"), ZoneOffset.UTC));
    }

    private GroupBuy group(LocalDateTime start, LocalDateTime end) {
        GroupBuy group = GroupBuy.create(1L, 2L, 50000, 5, 10000, 0, 0,
                BigDecimal.ZERO, 0, 50000, start, end);
        ReflectionTestUtils.setField(group, "groupBuyId", 100L);
        when(groups.findById(100L)).thenReturn(Optional.of(group));
        return group;
    }

    @ParameterizedTest
    @CsvSource({"CARD,BEFORE", "CARD,END", "CARD,AFTER", "KAKAOPAY,BEFORE", "KAKAOPAY,END",
            "KAKAOPAY,AFTER", "CRYPTO,BEFORE", "CRYPTO,END", "CRYPTO,AFTER"})
    void rejectsOutsideRecruitmentWithoutSlotOrPayment(String method, String boundary) {
        LocalDateTime start = "BEFORE".equals(boundary) ? NOW.plusSeconds(1) : NOW.minusDays(1);
        LocalDateTime end = "BEFORE".equals(boundary) ? NOW.plusDays(1)
                : "END".equals(boundary) ? NOW : NOW.minusSeconds(1);
        GroupBuy group = group(start, end);
        var request = new PaymentPrepareRequest(method, 10L, "CRYPTO".equals(method) ? 1L : null);
        assertThatThrownBy(() -> transaction.execute(1L, 100L, request, "window-key"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GROUP_BUY_NOT_RECRUITING));
        assertThat(group.getReservedSlots()).isZero();
        verify(payments, never()).saveAndFlush(any());
    }

    @Test void exactStartCanReserveAndExistingTtlRemains() {
        GroupBuy group = group(NOW, NOW.plusDays(1));
        var response = transaction.execute(1L, 100L, new PaymentPrepareRequest("CARD", 10L, null), "start-key");
        assertThat(group.getReservedSlots()).isEqualTo(1);
        assertThat(response.remainingSeconds()).isEqualTo(300);
        verify(payments).saveAndFlush(any());
    }

    @Test void oneSecondBeforeEndCanReserve() {
        GroupBuy group = group(NOW.minusDays(1), NOW.plusSeconds(1));
        transaction.execute(1L, 100L, new PaymentPrepareRequest("KAKAOPAY", 10L, null), "last-key");
        assertThat(group.getReservedSlots()).isEqualTo(1);
    }
}
