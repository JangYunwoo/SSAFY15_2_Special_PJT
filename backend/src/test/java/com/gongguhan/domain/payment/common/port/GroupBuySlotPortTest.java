package com.gongguhan.domain.payment.common.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gongguhan.domain.payment.common.port.GroupBuySlotPort.GroupBuySlot;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GroupBuySlotPortTest {

    @Test
    void totalAmountAddsShareAndShippingFeeExactly() {
        GroupBuySlot slot = slot(Long.MAX_VALUE - 1, 1);

        assertEquals(Long.MAX_VALUE, slot.totalAmount());
    }

    @Test
    void totalAmountRejectsOverflow() {
        GroupBuySlot slot = slot(Long.MAX_VALUE, 1);

        assertThrows(ArithmeticException.class, slot::totalAmount);
    }

    private GroupBuySlot slot(long shareAmount, long shippingFee) {
        return new GroupBuySlot(
                1L,
                shareAmount,
                shippingFee,
                2,
                0,
                0,
                LocalDateTime.of(2026, 9, 10, 0, 0),
                "RECRUITING");
    }
}
