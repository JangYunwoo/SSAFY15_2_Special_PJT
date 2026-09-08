package com.gongguhan.domain.groupbuy.service;

import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.gongguhan.domain.groupbuy.dto.BuyerParticipationSummaryResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuyResponse;
import com.gongguhan.domain.groupbuy.repository.GroupBuyCommandRepository;
import com.gongguhan.domain.groupbuy.repository.GroupBuyCreateCommand;
import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.repository.ProductRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupBuyService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.05");
    private static final String FEE_POLICY_RATE = "RATE";
    private static final Set<String> GROUP_BUY_STATUSES = Set.of(
            "RECRUITING", "FULFILLED", "SHIPPING", "DELIVERED",
            "COMPLETED", "SETTLED", "FAILED", "CANCELED");
    private static final Set<String> PARTICIPATION_STATUSES = Set.of(
            "PAID", "FULFILLED", "SHIPPING", "DELIVERED", "CONFIRMED",
            "SETTLED", "CANCELED", "RETURNING", "RETURNED", "REFUNDED");

    private final GroupBuyCommandRepository groupBuyCommandRepository;
    private final ProductRepository productRepository;

    public GroupBuyService(GroupBuyCommandRepository groupBuyCommandRepository, ProductRepository productRepository) {
        this.groupBuyCommandRepository = groupBuyCommandRepository;
        this.productRepository = productRepository;
    }

    public Page<GroupBuySummaryResponse> getPublicGroupBuys(
            String keyword, Long categoryId, String status, String sort, int page, int size) {
        String normalizedStatus = normalizeStatus(status, GROUP_BUY_STATUSES, GroupBuy.STATUS_RECRUITING);
        GroupBuySearchCondition condition = new GroupBuySearchCondition(
                normalizeKeyword(keyword), categoryId, normalizedStatus, GroupBuySort.from(sort));
        return groupBuyQueryRepository.findPublicGroupBuys(condition, pageRequest(page, size));
    }

    public GroupBuyDetailResponse getPublicDetail(Long groupBuyId) {
        return groupBuyQueryRepository.findPublicDetail(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
    }

    public Page<BuyerParticipationSummaryResponse> getBuyerParticipations(
            Long userId, String status, int page, int size) {
        ensureAuthenticated(userId);
        String normalizedStatus = normalizeOptionalStatus(status, PARTICIPATION_STATUSES);
        return groupBuyQueryRepository.findBuyerParticipations(
                userId, normalizedStatus, pageRequest(page, size));
    }

    public Page<SellerGroupBuySummaryResponse> getSellerGroupBuys(Long sellerId, int page, int size) {
        ensureAuthenticated(sellerId);
        return groupBuyQueryRepository.findSellerGroupBuys(sellerId, pageRequest(page, size));
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

    public GroupBuyAmountPreviewResponse preview(Long sellerId, GroupBuyAmountPreviewRequest request) {
        ensureUsableSeller(sellerId);
        findOwnedProduct(sellerId, request.productId());
        return calculateAmounts(request.productPrice(), request.targetCount(), request.shippingFee());
    }

    @Transactional
    public GroupBuyResponse create(Long sellerId, GroupBuyCreateRequest request) {
        ensureUsableSeller(sellerId);
        findOwnedProduct(sellerId, request.productId());
        validateRecruitPeriod(request);

        GroupBuyAmountPreviewResponse amounts =
                calculateAmounts(request.productPrice(), request.targetCount(), request.shippingFee());
        GroupBuyCreateCommand command = new GroupBuyCreateCommand(
                request.productId(),
                sellerId,
                request.productPrice(),
                request.targetCount(),
                amounts.unitShareAmount(),
                request.shippingFee(),
                amounts.roundingSurplus(),
                amounts.platformFeeRate(),
                amounts.estimatedPlatformFee(),
                amounts.estimatedSettlementAmount(),
                toUtcLocalDateTime(request.recruitStartAt()),
                toUtcLocalDateTime(request.recruitEndAt()));
        GroupBuyCommandRepository.StoredGroupBuy stored = groupBuyCommandRepository.create(command);

        return new GroupBuyResponse(
                stored.groupBuyId(),
                command.productId(),
                command.sellerId(),
                command.productPrice(),
                command.targetParticipants(),
                command.unitShareAmount(),
                command.roundingDiff(),
                command.shippingFee(),
                command.platformFeeRate(),
                command.expectedPlatformFee(),
                command.expectedSettlementAmount(),
                command.recruitStartAt().atOffset(ZoneOffset.UTC),
                command.recruitEndAt().atOffset(ZoneOffset.UTC),
                0,
                0,
                "RECRUITING",
                stored.createdAt(),
                stored.updatedAt());
    }

    private GroupBuyAmountPreviewResponse calculateAmounts(long productPrice, int targetCount, long shippingFee) {
        try {
            long unitShareAmount = BigDecimal.valueOf(productPrice)
                    .divide(BigDecimal.valueOf(targetCount), 0, RoundingMode.CEILING)
                    .longValueExact();
            long confirmedShareTotal = Math.multiplyExact(unitShareAmount, targetCount);
            long roundingSurplus = Math.subtractExact(confirmedShareTotal, productPrice);
            long estimatedPlatformFee = BigDecimal.valueOf(confirmedShareTotal)
                    .multiply(PLATFORM_FEE_RATE)
                    .setScale(0, RoundingMode.FLOOR)
                    .longValueExact();
            long totalShippingFee = Math.multiplyExact(shippingFee, targetCount);
            long estimatedSettlementAmount = Math.subtractExact(
                    Math.addExact(confirmedShareTotal, totalShippingFee), estimatedPlatformFee);

            return new GroupBuyAmountPreviewResponse(
                    productPrice,
                    targetCount,
                    unitShareAmount,
                    roundingSurplus,
                    shippingFee,
                    PLATFORM_FEE_RATE,
                    estimatedPlatformFee,
                    estimatedSettlementAmount,
                    FEE_POLICY_RATE);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "계산 가능한 금액 범위를 초과했습니다.");
        }
    }

    private void ensureUsableSeller(Long sellerId) {
        if (sellerId == null || productRepository.countUsableSeller(sellerId) == 0) {
            throw new BusinessException(ErrorCode.SELLER_SUSPENDED);
        }
    }

    private Product findOwnedProduct(Long sellerId, Long productId) {
        return productRepository.findByProductIdAndSellerIdAndStatus(productId, sellerId, Product.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    private void validateRecruitPeriod(GroupBuyCreateRequest request) {
        if (!request.recruitEndAt().isAfter(request.recruitStartAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "모집 종료 시각은 시작 시각보다 늦어야 합니다.");
        }
    }

    private LocalDateTime toUtcLocalDateTime(OffsetDateTime dateTime) {
        return dateTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
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

    private String normalizeStatus(String status, Set<String> allowed, String defaultStatus) {
        String normalized = status == null || status.isBlank()
                ? defaultStatus
                : status.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 상태값입니다.");
        }
        return normalized;
    }

    private String normalizeOptionalStatus(String status, Set<String> allowed) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return normalizeStatus(status, allowed, null);
    }

    private void ensureAuthenticated(Long actorId) {
        if (actorId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
