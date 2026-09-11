package com.gongguhan.domain.groupbuy.service;

import com.gongguhan.domain.account.repository.SellerProfileRepository;
import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuyCreateRequest;
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
    private final ProductRepository productRepository;
    private final SellerProfileRepository sellerProfileRepository;

    public GroupBuyService(GroupBuyCommandRepository groupBuyCommandRepository,
                           ProductRepository productRepository,
                           SellerProfileRepository sellerProfileRepository) {
        this.groupBuyCommandRepository = groupBuyCommandRepository;
        this.productRepository = productRepository;
        this.sellerProfileRepository = sellerProfileRepository;
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
        if (!request.recruitEndAt().isAfter(request.recruitStartAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "모집 종료 시각은 시작 시각보다 늦어야 합니다.");
        }
    }

    private LocalDateTime toUtcLocalDateTime(OffsetDateTime dateTime) {
        return dateTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
