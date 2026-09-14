package com.gongguhan.domain.escrow.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.entity.EscrowStatus;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EscrowWriterAdapterTest {

    @Test
    void paymentHoldCommandCreatesOneHeldEscrowWithTheSameSnapshot() {
        EscrowRepository repository = mock(EscrowRepository.class);
        EscrowWriterAdapter adapter = new EscrowWriterAdapter(repository);
        LocalDateTime heldAt = LocalDateTime.of(2026, 9, 11, 2, 30);

        adapter.hold(101L, 201L, 54_000L, heldAt);

        ArgumentCaptor<Escrow> escrow = ArgumentCaptor.forClass(Escrow.class);
        verify(repository).save(escrow.capture());
        assertEquals(101L, escrow.getValue().getParticipationId());
        assertEquals(201L, escrow.getValue().getGroupBuyId());
        assertEquals(54_000L, escrow.getValue().getAmount());
        assertEquals(heldAt, escrow.getValue().getHeldAt());
        assertEquals(EscrowStatus.HELD, escrow.getValue().getStatus());
    }
}
