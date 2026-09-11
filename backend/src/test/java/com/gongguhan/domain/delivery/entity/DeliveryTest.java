package com.gongguhan.domain.delivery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DeliveryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    void readyCreatesWithoutInvoice() {
        Delivery delivery = Delivery.ready(1L);

        assertEquals(Delivery.STATUS_READY, delivery.getStatus());
        assertNull(delivery.getTrackingNumber());
    }

    @Test
    void registerInvoiceStoresValuesWhileReady() {
        Delivery delivery = Delivery.ready(1L);

        delivery.registerInvoice("CJ", "123456789012", NOW);

        assertEquals("CJ", delivery.getCourierCode());
        assertEquals("123456789012", delivery.getTrackingNumber());
        assertEquals(NOW, delivery.getShippedAt());
        assertEquals(Delivery.STATUS_READY, delivery.getStatus());
    }

    @Test
    void registerInvoiceReplacesValuesOnReRegistration() {
        Delivery delivery = Delivery.ready(1L);
        delivery.registerInvoice("CJ", "123456789012", NOW);

        delivery.registerInvoice("HANJIN", "999999999999", NOW.plusHours(1));

        assertEquals("HANJIN", delivery.getCourierCode());
        assertEquals("999999999999", delivery.getTrackingNumber());
    }

    @Test
    void registerInvoiceRejectedAfterShipping() {
        Delivery delivery = Delivery.ready(1L);
        delivery.registerInvoice("CJ", "123456789012", NOW);
        delivery.markShipping();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> delivery.registerInvoice("CJ", "000000000000", NOW));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }

    @Test
    void markShippingRequiresInvoice() {
        Delivery delivery = Delivery.ready(1L);

        BusinessException exception = assertThrows(BusinessException.class, delivery::markShipping);

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void markDeliveredRequiresShipping() {
        Delivery delivery = Delivery.ready(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> delivery.markDelivered(NOW));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }

    @Test
    void fullTransitionReadyShippingDelivered() {
        Delivery delivery = Delivery.ready(1L);
        delivery.registerInvoice("CJ", "123456789012", NOW);

        delivery.markShipping();
        assertEquals(Delivery.STATUS_SHIPPING, delivery.getStatus());

        delivery.markDelivered(NOW.plusDays(2));
        assertEquals(Delivery.STATUS_DELIVERED, delivery.getStatus());
        assertEquals(NOW.plusDays(2), delivery.getDeliveredAt());
    }
}
