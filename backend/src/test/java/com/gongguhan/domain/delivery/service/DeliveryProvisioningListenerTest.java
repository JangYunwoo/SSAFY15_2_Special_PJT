package com.gongguhan.domain.delivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.delivery.entity.Delivery;
import com.gongguhan.domain.delivery.repository.DeliveryRepository;
import com.gongguhan.domain.groupbuy.event.GroupBuyFulfilledEvent;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeliveryProvisioningListenerTest {

    private final ParticipationRepository participationRepository = mock(ParticipationRepository.class);
    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryProvisioningListener listener =
            new DeliveryProvisioningListener(participationRepository, deliveryRepository);

    @Test
    void onFulfilledMarksPaidParticipationsAndCreatesReadyDeliveries() {
        Participation first = Participation.create(100L, 1L, 50000L, 3000L, 53000L,
                "홍길동", "010-1234-5678", "06236", "서울시 강남구", "101동 101호");
        Participation second = Participation.create(100L, 2L, 50000L, 3000L, 53000L,
                "김철수", "010-2222-3333", "06236", "서울시 강남구", "202동 202호");
        when(participationRepository.findByGroupBuyIdAndStatus(100L, Participation.STATUS_PAID))
                .thenReturn(List.of(first, second));

        listener.onFulfilled(new GroupBuyFulfilledEvent(
                100L, 10L, 2, 50000L, LocalDateTime.now(), LocalDateTime.now().plusDays(3)));

        assertEquals(Participation.STATUS_FULFILLED, first.getStatus());
        assertEquals(Participation.STATUS_FULFILLED, second.getStatus());
        ArgumentCaptor<Delivery> deliveryCaptor = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveryRepository, times(2)).save(deliveryCaptor.capture());
        for (Delivery delivery : deliveryCaptor.getAllValues()) {
            assertEquals(Delivery.STATUS_READY, delivery.getStatus());
        }
    }
}
