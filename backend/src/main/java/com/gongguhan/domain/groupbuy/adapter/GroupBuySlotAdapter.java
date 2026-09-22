package com.gongguhan.domain.groupbuy.adapter;

import com.gongguhan.domain.groupbuy.dto.GroupBuySlotSnapshot;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotService;
import com.gongguhan.domain.payment.common.port.GroupBuySlotPort;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GroupBuySlotAdapter implements GroupBuySlotPort {

    private final GroupBuySlotService groupBuySlotService;

    public GroupBuySlotAdapter(GroupBuySlotService groupBuySlotService) {
        this.groupBuySlotService = groupBuySlotService;
    }

    @Override
    public GroupBuySlot get(Long groupBuyId) {
        return toPort(groupBuySlotService.get(groupBuyId));
    }

    @Override
    public Map<Long, GroupBuySlot> getAll(Collection<Long> groupBuyIds) {
        return groupBuySlotService.getAll(groupBuyIds).values().stream()
                .map(this::toPort)
                .collect(Collectors.toUnmodifiableMap(GroupBuySlot::groupBuyId, Function.identity()));
    }

    @Override
    public GroupBuySlot reserve(Long groupBuyId) {
        return toPort(groupBuySlotService.reserve(groupBuyId));
    }

    @Override
    public GroupBuySlot release(Long groupBuyId) {
        return toPort(groupBuySlotService.release(groupBuyId));
    }

    @Override
    public GroupBuySlot confirm(Long groupBuyId) {
        return toPort(groupBuySlotService.confirm(groupBuyId));
    }

    @Override
    public ConfirmOutcome confirmForCompletion(Long groupBuyId) {
        GroupBuySlotService.ConfirmForCompletion result =
                groupBuySlotService.confirmForCompletion(groupBuyId);
        GroupBuySlot slot = toPort(result.snapshot());
        return result.confirmed()
                ? ConfirmOutcome.confirmed(slot)
                : ConfirmOutcome.notRecruiting(slot);
    }

    private GroupBuySlot toPort(GroupBuySlotSnapshot snapshot) {
        return new GroupBuySlot(
                snapshot.groupBuyId(),
                snapshot.unitShareAmount(),
                snapshot.shippingFee(),
                snapshot.targetParticipants(),
                snapshot.currentParticipants(),
                snapshot.reservedSlots(),
                snapshot.recruitEndAt(),
                snapshot.status());
    }
}
