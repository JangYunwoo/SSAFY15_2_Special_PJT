package com.gongguhan.domain.groupbuy.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.groupbuy.dto.GroupBuySlotSnapshot;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotService;
import com.gongguhan.domain.payment.common.port.GroupBuySlotPort.GroupBuySlot;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupBuySlotAdapterTest {

    private static final Long GROUP_BUY_ID = 100L;

    private GroupBuySlotService groupBuySlotService;
    private GroupBuySlotAdapter adapter;
    private GroupBuySlotSnapshot snapshot;

    @BeforeEach
    void setUp() {
        groupBuySlotService = mock(GroupBuySlotService.class);
        adapter = new GroupBuySlotAdapter(groupBuySlotService);
        snapshot = new GroupBuySlotSnapshot(
                GROUP_BUY_ID,
                50_000L,
                4_000L,
                10,
                3,
                1,
                LocalDateTime.of(2026, 9, 10, 12, 0),
                "RECRUITING");
    }

    @Test
    void reserveDelegatesAndMapsEveryContractField() {
        when(groupBuySlotService.reserve(GROUP_BUY_ID)).thenReturn(snapshot);

        GroupBuySlot result = adapter.reserve(GROUP_BUY_ID);

        verify(groupBuySlotService).reserve(GROUP_BUY_ID);
        assertEquals(snapshot.groupBuyId(), result.groupBuyId());
        assertEquals(snapshot.unitShareAmount(), result.unitShareAmount());
        assertEquals(snapshot.shippingFee(), result.shippingFee());
        assertEquals(snapshot.targetParticipants(), result.targetParticipants());
        assertEquals(snapshot.currentParticipants(), result.currentParticipants());
        assertEquals(snapshot.reservedSlots(), result.reservedSlots());
        assertEquals(snapshot.recruitEndAt(), result.recruitEndAt());
        assertEquals(snapshot.status(), result.status());
    }

    @Test
    void allOperationsDelegateToGroupBuyOwnedService() {
        when(groupBuySlotService.get(GROUP_BUY_ID)).thenReturn(snapshot);
        when(groupBuySlotService.release(GROUP_BUY_ID)).thenReturn(snapshot);
        when(groupBuySlotService.confirm(GROUP_BUY_ID)).thenReturn(snapshot);

        adapter.get(GROUP_BUY_ID);
        adapter.release(GROUP_BUY_ID);
        adapter.confirm(GROUP_BUY_ID);

        verify(groupBuySlotService).get(GROUP_BUY_ID);
        verify(groupBuySlotService).release(GROUP_BUY_ID);
        verify(groupBuySlotService).confirm(GROUP_BUY_ID);
    }

    @Test
    void getAllDelegatesOnceAndMapsSnapshotsById() {
        when(groupBuySlotService.getAll(Set.of(GROUP_BUY_ID)))
                .thenReturn(Map.of(GROUP_BUY_ID, snapshot));

        Map<Long, GroupBuySlot> result = adapter.getAll(Set.of(GROUP_BUY_ID));

        verify(groupBuySlotService).getAll(Set.of(GROUP_BUY_ID));
        assertEquals(GROUP_BUY_ID, result.get(GROUP_BUY_ID).groupBuyId());
        assertEquals(snapshot.unitShareAmount(), result.get(GROUP_BUY_ID).unitShareAmount());
    }
}
