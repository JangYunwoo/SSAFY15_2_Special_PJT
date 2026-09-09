package com.gongguhan.domain.groupbuy.service;

import com.gongguhan.domain.groupbuy.dto.GroupBuySlotSnapshot;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
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
