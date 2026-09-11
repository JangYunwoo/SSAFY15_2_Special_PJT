package com.gongguhan.domain.groupbuy.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongguhan.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GroupBuyOperationsTest {

    @Test
    void activePaymentSlotBlocksEditAndCancel() {
        GroupBuy groupBuy = groupBuy();
        groupBuy.reserveSlot();

        assertThat(groupBuy.canEditRecruitment()).isFalse();
        assertThatThrownBy(groupBuy::cancelBeforeParticipation)
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> groupBuy.updateRecruitment(
                2L, 900_000L, 10, 90_000L, 3_000L, 0L,
                new BigDecimal("0.05"), 45_000L, 885_000L,
                LocalDateTime.of(2026, 9, 8, 0, 0),
                LocalDateTime.of(2026, 9, 15, 0, 0)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void editReplacesAllDerivedAmountSnapshotsTogether() {
        GroupBuy groupBuy = groupBuy();

        groupBuy.updateRecruitment(
                2L, 900_001L, 10, 90_001L, 3_000L, 9L,
                new BigDecimal("0.05"), 45_000L, 885_010L,
                LocalDateTime.of(2026, 9, 9, 0, 0),
                LocalDateTime.of(2026, 9, 16, 0, 0));

        assertThat(groupBuy.getProductId()).isEqualTo(2L);
        assertThat(groupBuy.getProductPrice()).isEqualTo(900_001L);
        assertThat(groupBuy.getTargetParticipants()).isEqualTo(10);
        assertThat(groupBuy.getUnitShareAmount()).isEqualTo(90_001L);
        assertThat(groupBuy.getRoundingDiff()).isEqualTo(9L);
    }

    private GroupBuy groupBuy() {
        return GroupBuy.create(
                1L, 10L, 1_000_000L, 20, 50_000L, 4_000L, 0L,
                new BigDecimal("0.05"), 50_000L, 1_030_000L,
                LocalDateTime.of(2026, 9, 8, 0, 0),
                LocalDateTime.of(2026, 9, 15, 0, 0));
    }
}
