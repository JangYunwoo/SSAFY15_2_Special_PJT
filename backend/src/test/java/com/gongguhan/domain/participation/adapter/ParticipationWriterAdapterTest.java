package com.gongguhan.domain.participation.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.port.ParticipationWriterPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParticipationWriterAdapterTest {

    private static final Long GROUP_BUY_ID = 100L;
    private static final Long USER_ID = 1L;

    private ParticipationRepository participationRepository;
    private ParticipationWriterAdapter adapter;

    @BeforeEach
    void setUp() {
        participationRepository = mock(ParticipationRepository.class);
        adapter = new ParticipationWriterAdapter(participationRepository);
    }

    @Test
    void existsActiveParticipationDelegatesToRepository() {
        when(participationRepository.existsByGroupBuyIdAndUserIdAndActiveMarker(
                GROUP_BUY_ID, USER_ID, Participation.ACTIVE_MARKER)).thenReturn(true);

        boolean result = adapter.existsActiveParticipation(GROUP_BUY_ID, USER_ID);

        assertEquals(true, result);
    }

    @Test
    void createSavesParticipationAndReturnsRecord() {
        when(participationRepository.saveAndFlush(any(Participation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ParticipationWriterPort.ParticipationRecord result = adapter.create(
                new ParticipationWriterPort.ParticipationCreateCommand(
                        GROUP_BUY_ID, USER_ID, 50000L, 3000L, 53000L,
                        "홍길동", "010-1234-5678", "06236", "서울시 강남구", "101동 101호"));

        verify(participationRepository).saveAndFlush(any(Participation.class));
        assertEquals(GROUP_BUY_ID, result.groupBuyId());
        assertEquals(USER_ID, result.userId());
    }
}
