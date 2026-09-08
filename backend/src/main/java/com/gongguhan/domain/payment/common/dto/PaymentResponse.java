package com.gongguhan.domain.payment.common.dto;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.payment.common.entity.Payment;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record PaymentResponse(
        String paymentId,
        String groupBuyId,
        String status,
        String method,
        AmountResponse amount,
        String slotExpiresAt,
        long remainingSeconds
) {

    public record AmountResponse(
            long shareAmount,
            long shippingFee,
            long totalAmount,
            String currency
    ) {
    }

    public static PaymentResponse from(Payment payment, GroupBuy groupBuy, LocalDateTime now) {
        AmountResponse amount = new AmountResponse(
                groupBuy.getUnitShareAmount(),
                groupBuy.getShippingFee(),
                payment.getKrwAmount(),
                "KRW");
        long remainingSeconds = Math.max(0,
                java.time.Duration.between(now, payment.getSlotExpiresAt()).getSeconds());
        return new PaymentResponse(
                String.valueOf(payment.getPaymentId()),
                String.valueOf(payment.getGroupBuyId()),
                payment.getStatus(),
                payment.getMethod(),
                amount,
                payment.getSlotExpiresAt().atOffset(ZoneOffset.UTC).toString(),
                payment.isProcessing() ? remainingSeconds : 0);
    }
}
