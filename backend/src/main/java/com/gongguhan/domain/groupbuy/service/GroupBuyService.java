package com.gongguhan.domain.groupbuy.service;

import com.gongguhan.domain.account.repository.SellerProfileRepository;
import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyUpdateRequest;
import com.gongguhan.domain.groupbuy.dto.SellerGroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyCommandRepository;
import com.gongguhan.domain.groupbuy.repository.GroupBuyCreateCommand;
import com.gongguhan.domain.groupbuy.repository.GroupBuyQueryRepository;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.repository.ProductRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupBuyService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.05");
    private static final String FEE_POLICY_RATE = "RATE";
    private static final List<String> USABLE_SELLER_STATUSES = List.of("ACTIVE", "WARNED");

    private final GroupBuyCommandRepository groupBuyCommandRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyQueryRepository groupBuyQueryRepository;
    private final ProductRepository productRepository;
    private final SellerProfileRepository sellerProfileRepository;

    public GroupBuyService(GroupBuyCommandRepository groupBuyCommandRepository,
                           GroupBuyRepository groupBuyRepository,
                           GroupBuyQueryRepository groupBuyQueryRepository,
                           ProductRepository productRepository,
                           SellerProfileRepository sellerProfileRepository) {
        this.groupBuyCommandRepository = groupBuyCommandRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyQueryRepository = groupBuyQueryRepository;
        this.productRepository = productRepository;
        this.sellerProfileRepository = sellerProfileRepository;
    }

    public GroupBuyAmountPreviewResponse preview(Long sellerId, GroupBuyAmountPreviewRequest request) {
        ensureUsableSeller(sellerId);
        findOwnedProduct(sellerId, request.productId());
        return calculateAmounts(request.productPrice(), request.targetCount(), request.shippingFee());
    }

    @Transactional
    public SellerGroupBuyDetailResponse create(Long sellerId, GroupBuyCreateRequest request) {
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
        return sellerDetail(sellerId, stored.groupBuyId());
    }

    @Transactional
    public SellerGroupBuyDetailResponse update(
            Long sellerId, Long groupBuyId, GroupBuyUpdateRequest request) {
        ensureUsableSeller(sellerId);
        GroupBuy groupBuy = ownedGroupBuy(sellerId, groupBuyId);

        Long productId = request.productId() == null ? groupBuy.getProductId() : request.productId();
        findOwnedProduct(sellerId, productId);
        long productPrice = request.productPrice() == null
                ? groupBuy.getProductPrice() : request.productPrice();
        int targetCount = request.targetCount() == null
                ? groupBuy.getTargetParticipants() : request.targetCount();
        long shippingFee = request.shippingFee() == null
                ? groupBuy.getShippingFee() : request.shippingFee();
        LocalDateTime recruitStartAt = request.recruitStartAt() == null
                ? groupBuy.getRecruitStartAt() : toUtcLocalDateTime(request.recruitStartAt());
        LocalDateTime recruitEndAt = request.recruitEndAt() == null
                ? groupBuy.getRecruitEndAt() : toUtcLocalDateTime(request.recruitEndAt());
        validateRecruitPeriod(recruitStartAt, recruitEndAt);

        GroupBuyAmountPreviewResponse amounts = calculateAmounts(productPrice, targetCount, shippingFee);
        groupBuy.updateRecruitment(
                productId, productPrice, targetCount, amounts.unitShareAmount(), shippingFee,
                amounts.roundingSurplus(), amounts.platformFeeRate(), amounts.estimatedPlatformFee(),
                amounts.estimatedSettlementAmount(), recruitStartAt, recruitEndAt);
        groupBuyRepository.flush();
        return sellerDetail(sellerId, groupBuyId);
    }

    @Transactional
    public void cancel(Long sellerId, Long groupBuyId, String reason) {
        ensureUsableSeller(sellerId);
        normalizeReason(reason);
        ownedGroupBuy(sellerId, groupBuyId).cancelBeforeParticipation();
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
        if (sellerId == null
                || !sellerProfileRepository.existsBySellerIdAndStatusIn(
                        sellerId, USABLE_SELLER_STATUSES)) {
            throw new BusinessException(ErrorCode.SELLER_SUSPENDED);
        }
    }

    private Product findOwnedProduct(Long sellerId, Long productId) {
        return productRepository.findByProductIdAndSellerIdAndStatus(productId, sellerId, Product.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    private void validateRecruitPeriod(GroupBuyCreateRequest request) {
        validateRecruitPeriod(
                toUtcLocalDateTime(request.recruitStartAt()),
                toUtcLocalDateTime(request.recruitEndAt()));
    }

    private void validateRecruitPeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "모집 종료 시각은 시작 시각보다 늦어야 합니다.");
        }
    }

    private GroupBuy ownedGroupBuy(Long sellerId, Long groupBuyId) {
        return groupBuyRepository.findByGroupBuyIdAndSellerId(groupBuyId, sellerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
    }

    private SellerGroupBuyDetailResponse sellerDetail(Long sellerId, Long groupBuyId) {
        return groupBuyQueryRepository.findSellerDetail(sellerId, groupBuyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "취소 사유는 필수입니다.");
        }
        return reason.trim();
    }

    private LocalDateTime toUtcLocalDateTime(OffsetDateTime dateTime) {
        return dateTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
