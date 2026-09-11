package com.gongguhan.domain.returnrequest.service;

import com.gongguhan.domain.returnrequest.dto.ReturnCreateRequest;
import com.gongguhan.domain.returnrequest.dto.ReturnDetailResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnPreviewRequest;
import com.gongguhan.domain.returnrequest.dto.ReturnPreviewResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnSummaryResponse;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.CreateCommand;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.ReturnContext;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.ReturnLock;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReturnRequestService {

    public static final String REASON_CHANGE_OF_MIND = "CHANGE_OF_MIND";
    public static final String REASON_DEFECT = "DEFECT";
    private static final int SELLER_RESPONSE_BUSINESS_DAYS = 3;

    private final ReturnRequestRepository repository;
    private final ReturnBusinessDayCalculator businessDays;
    private final Clock clock;

    public ReturnRequestService(ReturnRequestRepository repository,
                                ReturnBusinessDayCalculator businessDays,
                                Clock clock) {
        this.repository = repository;
        this.businessDays = businessDays;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReturnPreviewResponse preview(Long userId, Long participationId,
                                         ReturnPreviewRequest request) {
        validateReason(request.reasonType());
        ReturnContext context = findContext(participationId, false);
        requireBuyer(userId, context);
        return calculatePreview(context, request.reasonType(), now());
    }

    @Transactional
    public ReturnDetailResponse create(Long userId, Long participationId,
                                       ReturnCreateRequest request) {
        validateReason(request.reasonType());
        ReturnContext context = findContext(participationId, true);
        requireBuyer(userId, context);
        LocalDateTime requestedAt = now();
        ReturnPreviewResponse preview = calculatePreview(context, request.reasonType(), requestedAt);
        if (!preview.eligible()) {
            throw new BusinessException(ErrorCode.RETURN_NOT_ELIGIBLE, preview.ineligibleReason());
        }
        if (context.confirmationHeld() || repository.existsOpenReturn(participationId)) {
            throw new BusinessException(ErrorCode.PURCHASE_CONFIRM_RETURN_CONFLICT,
                    "이미 진행 중인 반품이 있습니다.");
        }

        boolean postConfirm = "CONFIRMED".equals(context.participationStatus());
        boolean adminReviewRequired = postConfirm;
        LocalDateTime autoApproveAt = adminReviewRequired ? null
                : businessDays.add(requestedAt, SELLER_RESPONSE_BUSINESS_DAYS);
        Long returnId = repository.create(new CreateCommand(
                participationId, request.reasonType(), normalize(request.detailReason()),
                preview.returnShippingFee(), preview.shippingFeeBearer(), preview.refundAmount(),
                postConfirm, adminReviewRequired, requestedAt, autoApproveAt,
                request.pickupZipcode(), request.pickupAddress1().trim(),
                normalize(request.pickupAddress2())));
        repository.holdConfirmation(participationId);
        repository.addHistory(returnId, "REQUESTED",
                adminReviewRequired ? "구매확정 후 접수되어 관리자 검토가 필요합니다." : null,
                requestedAt);
        return findBuyerDetail(userId, returnId);
    }

    @Transactional(readOnly = true)
    public Page<ReturnSummaryResponse> buyerList(Long userId, String status,
                                                 Long groupBuyId, Pageable pageable) {
        requireAuthenticated(userId);
        return repository.findBuyerList(userId, normalize(status), groupBuyId, pageable);
    }

    @Transactional(readOnly = true)
    public ReturnDetailResponse findBuyerDetail(Long userId, Long returnId) {
        requireAuthenticated(userId);
        return repository.findBuyerDetail(userId, returnId)
                .orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public Page<ReturnSummaryResponse> sellerList(Long sellerId, String status,
                                                  Long groupBuyId, Pageable pageable) {
        requireAuthenticated(sellerId);
        return repository.findSellerList(sellerId, normalize(status), groupBuyId, pageable);
    }

    @Transactional(readOnly = true)
    public ReturnDetailResponse findSellerDetail(Long sellerId, Long returnId) {
        requireAuthenticated(sellerId);
        return repository.findSellerDetail(sellerId, returnId).orElseThrow(this::notFound);
    }

    @Transactional
    public ReturnDetailResponse approve(Long sellerId, Long returnId) {
        ReturnLock target = findSellerLock(sellerId, returnId);
        requireRequested(target);
        if (target.adminReviewRequired()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "구매확정 후 반품은 관리자 검토가 필요합니다.");
        }
        LocalDateTime approvedAt = now();
        repository.approve(returnId, "MANUAL", approvedAt);
        repository.addHistory(returnId, "APPROVED", "판매자가 반품을 승인했습니다.", approvedAt);
        return loadSellerDetail(sellerId, returnId);
    }

    @Transactional
    public ReturnDetailResponse reject(Long sellerId, Long returnId, String reason) {
        ReturnLock target = findSellerLock(sellerId, returnId);
        requireRequested(target);
        if (target.adminReviewRequired()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "구매확정 후 반품은 관리자 검토가 필요합니다.");
        }
        LocalDateTime rejectedAt = now();
        repository.reject(returnId, reason.trim());
        repository.addHistory(returnId, "REJECTED", reason.trim(), rejectedAt);
        return loadSellerDetail(sellerId, returnId);
    }

    @Transactional
    public void dispute(Long userId, Long returnId, String detailReason) {
        ReturnLock target = repository.findBuyerLock(userId, returnId)
                .orElseThrow(this::notFound);
        if (!"REJECTED".equals(target.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "거부된 반품만 이의를 제기할 수 있습니다.");
        }
        LocalDateTime disputedAt = now();
        repository.dispute(returnId);
        repository.addHistory(returnId, "DISPUTED", detailReason.trim(), disputedAt);
    }

    @Transactional
    public boolean approveAutomatically(Long returnId) {
        LocalDateTime approvedAt = now();
        boolean approved = repository.approveAutomatically(returnId, approvedAt);
        if (approved) {
            repository.addHistory(returnId, "APPROVED", "판매자 무응답으로 자동 승인되었습니다.", approvedAt);
        }
        return approved;
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> autoApprovalCandidates(int limit) {
        return repository.findAutoApprovalCandidates(now(), limit);
    }

    private ReturnPreviewResponse calculatePreview(ReturnContext context, String reasonType,
                                                    LocalDateTime requestedAt) {
        String ineligibleReason = ineligibleReason(context, reasonType, requestedAt);
        boolean changeOfMind = REASON_CHANGE_OF_MIND.equals(reasonType);
        long originalShippingRefund = changeOfMind ? 0 : context.originalShippingFee();
        long returnShippingFee = context.returnShippingFee();
        long refundAmount = changeOfMind
                ? Math.max(0, context.shareAmount() - returnShippingFee)
                : context.totalAmount();
        return new ReturnPreviewResponse(
                ineligibleReason == null, ineligibleReason,
                context.withdrawalDeadline() == null ? null
                        : context.withdrawalDeadline().atOffset(ZoneOffset.UTC),
                context.totalAmount(), context.shareAmount(), originalShippingRefund,
                returnShippingFee, refundAmount, changeOfMind ? "BUYER" : "SELLER");
    }

    private String ineligibleReason(ReturnContext context, String reasonType,
                                    LocalDateTime requestedAt) {
        if (!"DELIVERED".equals(context.participationStatus())
                && !"CONFIRMED".equals(context.participationStatus())) {
            return "배송 완료된 주문만 반품할 수 있습니다.";
        }
        if (context.withdrawalDeadline() == null
                || requestedAt.isAfter(context.withdrawalDeadline())) {
            return "청약철회 기간이 지났습니다.";
        }
        if ("CONFIRMED".equals(context.participationStatus())
                && (context.settlementEligibleAt() == null
                || !requestedAt.isBefore(context.settlementEligibleAt()))) {
            return "정산 대상이 된 구매확정 주문은 반품할 수 없습니다.";
        }
        if (context.withdrawalRestricted() && !REASON_DEFECT.equals(reasonType)) {
            return "청약철회 제한 상품은 하자 사유로만 반품할 수 있습니다.";
        }
        return null;
    }

    private ReturnContext findContext(Long participationId, boolean forUpdate) {
        return repository.findContext(participationId, forUpdate)
                .orElseThrow(this::notFound);
    }

    private ReturnLock findSellerLock(Long sellerId, Long returnId) {
        requireAuthenticated(sellerId);
        return repository.findSellerLock(sellerId, returnId).orElseThrow(this::notFound);
    }

    private ReturnDetailResponse loadSellerDetail(Long sellerId, Long returnId) {
        return repository.findSellerDetail(sellerId, returnId).orElseThrow(this::notFound);
    }

    private void requireBuyer(Long userId, ReturnContext context) {
        requireAuthenticated(userId);
        if (!context.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }
    }

    private void requireAuthenticated(Long principalId) {
        if (principalId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    private void requireRequested(ReturnLock target) {
        if (!"REQUESTED".equals(target.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "요청 상태의 반품만 처리할 수 있습니다.");
        }
    }

    private void validateReason(String reasonType) {
        if (!REASON_CHANGE_OF_MIND.equals(reasonType) && !REASON_DEFECT.equals(reasonType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "반품 사유는 CHANGE_OF_MIND 또는 DEFECT여야 합니다.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "반품 정보를 찾을 수 없습니다.");
    }
}
