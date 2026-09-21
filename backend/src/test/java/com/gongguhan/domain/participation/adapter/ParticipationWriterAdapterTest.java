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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.mockito.ArgumentCaptor;

class ParticipationWriterAdapterTest {

    private static final Long GROUP_BUY_ID = 100L;
    private static final Long USER_ID = 1L;

    private ParticipationRepository participationRepository;
    private ParticipationWriterAdapter adapter;

    @BeforeEach
    void setUp() {
        participationRepository = mock(ParticipationRepository.class);
        adapter = new ParticipationWriterAdapter(participationRepository,
                Clock.fixed(Instant.parse("2026-09-21T03:04:05Z"), ZoneId.of("Asia/Seoul")));
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
        ArgumentCaptor<Participation> captured = ArgumentCaptor.forClass(Participation.class);
        verify(participationRepository).saveAndFlush(captured.capture());
        assertEquals(LocalDateTime.of(2026, 9, 21, 3, 4, 5), captured.getValue().getJoinedAt());
    }

    @Test
    void explicitUtcTimestampIsPreservedWithoutHostTimeLookup() {
        LocalDateTime utc = LocalDateTime.of(2020, 1, 2, 3, 4, 5);
        Participation participation = Participation.create(100L, 1L, 1000, 0, 1000,
                "테스트", "01012345678", "04524", "서울", "101호", utc);
        assertEquals(utc, participation.getJoinedAt());
    }
}
