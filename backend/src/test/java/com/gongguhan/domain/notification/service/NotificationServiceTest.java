package com.gongguhan.domain.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.notification.entity.Notification;
import com.gongguhan.domain.notification.repository.NotificationRepository;
import com.gongguhan.domain.penalty.event.SanctionLevel;
import com.gongguhan.domain.penalty.event.SellerSanctionedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(notificationRepository);

    private SellerSanctionedEvent event(SanctionLevel level, long penaltyId, int count) {
        return new SellerSanctionedEvent(10L, penaltyId, level, count, "NO_INVOICE",
                LocalDateTime.now());
    }

    @Test
    void warningSanctionStoresWarningNotificationForSeller() {
        when(notificationRepository.existsByDedupKey(any())).thenReturn(false);

        boolean created = service.notifySellerSanction(event(SanctionLevel.WARNING, 1L, 1));

        assertTrue(created);
        Notification saved = capturedSave();
        assertEquals(Notification.RECIPIENT_SELLER, saved.getRecipientType());
        assertEquals(10L, saved.getRecipientId());
        assertEquals(Notification.TYPE_SELLER_SANCTION_WARNING, saved.getType());
        assertEquals("SELLER_SANCTION:1:WARNING", saved.getDedupKey());
    }

    @Test
    void suspendedSanctionStoresSuspendedNotification() {
        when(notificationRepository.existsByDedupKey(any())).thenReturn(false);

        boolean created = service.notifySellerSanction(event(SanctionLevel.SUSPENDED, 2L, 2));

        assertTrue(created);
        assertEquals(Notification.TYPE_SELLER_SANCTION_SUSPENDED, capturedSave().getType());
    }

    @Test
    void threeStrikeSanctionStoresThreeStrikeNotification() {
        when(notificationRepository.existsByDedupKey(any())).thenReturn(false);

        boolean created = service.notifySellerSanction(event(SanctionLevel.THREE_STRIKE, 3L, 3));

        assertTrue(created);
        assertEquals(Notification.TYPE_SELLER_SANCTION_THREE_STRIKE, capturedSave().getType());
    }

    @Test
    void duplicateEventDoesNotStoreSecondNotification() {
        when(notificationRepository.existsByDedupKey("SELLER_SANCTION:1:WARNING")).thenReturn(true);

        boolean created = service.notifySellerSanction(event(SanctionLevel.WARNING, 1L, 1));

        assertFalse(created);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void concurrentInsertRaceIsHandledIdempotently() {
        when(notificationRepository.existsByDedupKey(any())).thenReturn(false);
        when(notificationRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uk_notifications_dedup"));

        boolean created = service.notifySellerSanction(event(SanctionLevel.WARNING, 1L, 1));

        assertFalse(created);
    }

    private Notification capturedSave() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }
}
