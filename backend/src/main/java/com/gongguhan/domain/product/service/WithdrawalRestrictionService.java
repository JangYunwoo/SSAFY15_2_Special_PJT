package com.gongguhan.domain.product.service;

import com.gongguhan.domain.product.dto.ProductResponse;
import com.gongguhan.domain.product.dto.WithdrawalRestrictionReasonResponse;
import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.entity.RestrictionReviewStatus;
import com.gongguhan.domain.product.repository.ProductRepository;
import com.gongguhan.domain.product.repository.WithdrawalRestrictionReasonRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WithdrawalRestrictionService {

    private final WithdrawalRestrictionReasonRepository reasonRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    public WithdrawalRestrictionService(
            WithdrawalRestrictionReasonRepository reasonRepository,
            ProductRepository productRepository,
            Clock clock) {
        this.reasonRepository = reasonRepository;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    public List<WithdrawalRestrictionReasonResponse> getActiveReasons() {
        return reasonRepository.findByActiveTrueOrderBySortOrderAscReasonIdAsc().stream()
                .map(WithdrawalRestrictionReasonResponse::from)
                .toList();
    }

    public List<ProductResponse> getPendingProducts() {
        return productRepository
                .findByWithdrawalRestrictedTrueAndRestrictionReviewStatusOrderByProductIdAsc(
                        RestrictionReviewStatus.PENDING)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse review(Long adminId, Long productId, RestrictionReviewStatus decision) {
        if (adminId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (decision != RestrictionReviewStatus.APPROVED && decision != RestrictionReviewStatus.REJECTED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "승인 또는 반려 상태만 지정할 수 있습니다.");
        }
        Product product = productRepository.findById(productId)
                .filter(Product::isWithdrawalRestricted)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "검토할 제한 상품을 찾을 수 없습니다."));
        try {
            product.reviewWithdrawalRestriction(adminId, decision, LocalDateTime.now(clock));
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, e.getMessage());
        }
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }
}
