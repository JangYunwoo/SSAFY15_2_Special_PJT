package com.gongguhan.domain.groupbuy.service;

import com.gongguhan.domain.groupbuy.dto.GroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuySearchCondition;
import com.gongguhan.domain.groupbuy.dto.GroupBuySort;
import com.gongguhan.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.gongguhan.domain.groupbuy.dto.SellerGroupBuySummaryResponse;
import com.gongguhan.domain.groupbuy.dto.SellerGroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.dto.SellerParticipantResponse;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyQueryRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupBuyQueryService {

    private static final Set<String> GROUP_BUY_STATUSES = Set.of(
            "RECRUITING", "FULFILLED", "SHIPPING", "DELIVERED",
            "COMPLETED", "SETTLED", "FAILED", "CANCELED");

    private final GroupBuyQueryRepository groupBuyQueryRepository;

    public GroupBuyQueryService(GroupBuyQueryRepository groupBuyQueryRepository) {
        this.groupBuyQueryRepository = groupBuyQueryRepository;
    }

    public Page<GroupBuySummaryResponse> getPublicGroupBuys(
            String keyword, Long categoryId, String status, String sort, int page, int size) {
        String normalizedStatus = normalizeStatus(status);
        GroupBuySearchCondition condition = new GroupBuySearchCondition(
                normalizeKeyword(keyword), categoryId, normalizedStatus, GroupBuySort.from(sort));
        return groupBuyQueryRepository.findPublicGroupBuys(condition, pageRequest(page, size));
    }

    public GroupBuyDetailResponse getPublicDetail(Long groupBuyId) {
        return groupBuyQueryRepository.findPublicDetail(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
    }

    public Page<SellerGroupBuySummaryResponse> getSellerGroupBuys(Long sellerId, int page, int size) {
        ensureAuthenticated(sellerId);
        return groupBuyQueryRepository.findSellerGroupBuys(sellerId, pageRequest(page, size));
    }

    public SellerGroupBuyDetailResponse getSellerDetail(Long sellerId, Long groupBuyId) {
        ensureAuthenticated(sellerId);
        return groupBuyQueryRepository.findSellerDetail(sellerId, groupBuyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
    }

    public Page<SellerParticipantResponse> getSellerParticipants(
            Long sellerId, Long groupBuyId, int page, int size) {
        ensureAuthenticated(sellerId);
        if (!groupBuyQueryRepository.existsOwnedGroupBuy(sellerId, groupBuyId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다.");
        }
        return groupBuyQueryRepository.findSellerParticipants(
                sellerId, groupBuyId, pageRequest(page, size));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page는 0 이상, size는 1 이상 100 이하여야 합니다.");
        }
        return PageRequest.of(page, size);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "검색어는 200자 이하여야 합니다.");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? GroupBuy.STATUS_RECRUITING
                : status.trim().toUpperCase(Locale.ROOT);
        if (!GROUP_BUY_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 상태값입니다.");
        }
        return normalized;
    }

    private void ensureAuthenticated(Long actorId) {
        if (actorId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
