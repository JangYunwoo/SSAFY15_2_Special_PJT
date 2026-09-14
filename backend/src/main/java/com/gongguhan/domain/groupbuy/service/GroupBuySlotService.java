package com.gongguhan.domain.groupbuy.service;

import com.gongguhan.domain.groupbuy.dto.GroupBuySlotSnapshot;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupBuySlotService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyRecruitmentService groupBuyRecruitmentService;

    public GroupBuySlotService(
            GroupBuyRepository groupBuyRepository,
            GroupBuyRecruitmentService groupBuyRecruitmentService) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyRecruitmentService = groupBuyRecruitmentService;
    }

    public GroupBuySlotSnapshot get(Long groupBuyId) {
        return snapshot(find(groupBuyId));
    }

    /**
     * 결제 이력처럼 여러 슬롯 스냅샷이 필요한 읽기 경로를 위한 일괄 조회다.
     * 중복 ID를 먼저 제거해 IN 절과 결과 매핑 크기를 줄이고, FK 불변식이 깨져
     * 요청한 공동구매가 하나라도 없으면 단건 조회와 동일하게 실패시킨다.
     */
    public Map<Long, GroupBuySlotSnapshot> getAll(Collection<Long> groupBuyIds) {
        if (groupBuyIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> uniqueIds = Set.copyOf(groupBuyIds);
        Map<Long, GroupBuySlotSnapshot> snapshots = groupBuyRepository.findAllById(uniqueIds).stream()
                .map(this::snapshot)
                .collect(Collectors.toUnmodifiableMap(
                        GroupBuySlotSnapshot::groupBuyId, Function.identity()));
        if (snapshots.size() != uniqueIds.size()) {
            throw notFound();
        }
        return snapshots;
    }

    @Transactional
    public GroupBuySlotSnapshot reserve(Long groupBuyId) {
        GroupBuy groupBuy = findWithLock(groupBuyId);
        groupBuy.reserveSlot();
        return snapshot(groupBuy);
    }

    @Transactional
    public GroupBuySlotSnapshot release(Long groupBuyId) {
        GroupBuy groupBuy = findWithLock(groupBuyId);
        groupBuy.releaseSlot();
        return snapshot(groupBuy);
    }

    @Transactional
    public GroupBuySlotSnapshot confirm(Long groupBuyId) {
        GroupBuy groupBuy = findWithLock(groupBuyId);
        groupBuy.confirmParticipant();
        groupBuyRecruitmentService.judgeTargetReached(groupBuy);
        return snapshot(groupBuy);
    }

    private GroupBuy find(Long groupBuyId) {
        return groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> notFound());
    }

    private GroupBuy findWithLock(Long groupBuyId) {
        return groupBuyRepository.findWithLockByGroupBuyId(groupBuyId)
                .orElseThrow(() -> notFound());
    }

    private GroupBuySlotSnapshot snapshot(GroupBuy groupBuy) {
        return new GroupBuySlotSnapshot(
                groupBuy.getGroupBuyId(),
                groupBuy.getUnitShareAmount(),
                groupBuy.getShippingFee(),
                groupBuy.getTargetParticipants(),
                groupBuy.getCurrentParticipants(),
                groupBuy.getReservedSlots(),
                groupBuy.getRecruitEndAt(),
                groupBuy.getStatus());
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다.");
    }
}
