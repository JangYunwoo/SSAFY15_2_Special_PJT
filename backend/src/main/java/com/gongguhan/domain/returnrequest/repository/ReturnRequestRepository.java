package com.gongguhan.domain.returnrequest.repository;

import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.repository.ProductRepository;
import com.gongguhan.domain.returnrequest.dto.ReturnDetailResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnHistoryResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnSummaryResponse;
import com.gongguhan.domain.returnrequest.entity.ReturnRequest;
import com.gongguhan.domain.returnrequest.entity.ReturnRequestHistory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * 반품 도메인이 사용하는 저장소 파사드.
 *
 * <p>SQL 문자열을 직접 조립하지 않고 Spring Data JPA 저장소와 엔티티 상태 전이를
 * 조합한다. 서비스는 조회 방식이 바뀌어도 동일한 반품 문맥만 보도록 한다.
 */
@Repository
public class ReturnRequestRepository {

    private static final List<String> TERMINAL_RETURN_STATUSES = List.of(
            ReturnRequest.STATUS_REFUNDED, ReturnRequest.STATUS_REJECTED);

    private final ReturnRequestJpaRepository requests;
    private final ReturnRequestHistoryRepository histories;
    private final ParticipationRepository participations;
    private final GroupBuyRepository groupBuys;
    private final ProductRepository products;
    private final UserRepository users;
    private final PaymentRepository payments;

    public ReturnRequestRepository(
            ReturnRequestJpaRepository requests,
            ReturnRequestHistoryRepository histories,
            ParticipationRepository participations,
            GroupBuyRepository groupBuys,
            ProductRepository products,
            UserRepository users,
            PaymentRepository payments) {
        this.requests = requests;
        this.histories = histories;
        this.participations = participations;
        this.groupBuys = groupBuys;
        this.products = products;
        this.users = users;
        this.payments = payments;
    }

    public Optional<ReturnContext> findContext(Long participationId, boolean forUpdate) {
        Optional<Participation> participation = forUpdate
                ? participations.findWithLockByParticipationId(participationId)
                : participations.findById(participationId);
        return participation.map(this::toContext);
    }

    public boolean existsOpenReturn(Long participationId) {
        return requests.existsByParticipationParticipationIdAndStatusNotIn(
                participationId, TERMINAL_RETURN_STATUSES);
    }

    public Long create(CreateCommand command) {
        Participation participation = participations.findById(command.participationId())
                .orElseThrow(() -> new IllegalStateException("반품 대상 참여 정보를 찾을 수 없습니다."));
        ReturnRequest request = ReturnRequest.request(
                participation, command.reasonType(), command.reasonDetail(),
                command.returnShippingFee(), command.feeBearer(), command.refundAmount(),
                command.postConfirm(), command.adminReviewRequired(), command.requestedAt(),
                command.autoApproveAt(), command.pickupZipcode(), command.pickupAddress1(),
                command.pickupAddress2());
        return requests.save(request).getReturnId();
    }

    public void holdConfirmation(Long participationId) {
        participation(participationId).holdConfirmation();
    }

    public void addHistory(Long returnId, String status, String note, LocalDateTime occurredAt) {
        histories.save(ReturnRequestHistory.record(
                requests.getReferenceById(returnId), status, note, occurredAt));
    }

    public Optional<ReturnDetailResponse> findBuyerDetail(Long userId, Long returnId) {
        return requests.findBuyerDetail(userId, returnId).map(this::toDetail);
    }

    public Optional<ReturnDetailResponse> findSellerDetail(Long sellerId, Long returnId) {
        return requests.findSellerDetail(sellerId, returnId).map(this::toDetail);
    }

    public Page<ReturnSummaryResponse> findBuyerList(Long userId, String status,
                                                     Long groupBuyId, Pageable pageable) {
        return requests.findBuyerList(userId, status, groupBuyId, pageable).map(this::toSummary);
    }

    public Page<ReturnSummaryResponse> findSellerList(Long sellerId, String status,
                                                      Long groupBuyId, Pageable pageable) {
        return requests.findSellerList(sellerId, status, groupBuyId, pageable).map(this::toSummary);
    }

    public Page<ReturnSummaryResponse> findAdminReviewList(String status, Long groupBuyId,
                                                            Pageable pageable) {
        return requests.findAdminReviewList(status, groupBuyId, pageable).map(this::toSummary);
    }

    public Optional<ReturnDetailResponse> findAdminDetail(Long returnId) {
        return requests.findDetailByReturnId(returnId).map(this::toDetail);
    }

    public Optional<ReturnLock> findSellerLock(Long sellerId, Long returnId) {
        return findLockedReturn(returnId)
                .filter(request -> groupBuy(request.getParticipation()).getSellerId().equals(sellerId))
                .map(this::toLock);
    }

    public Optional<ReturnLock> findBuyerLock(Long userId, Long returnId) {
        return findLockedReturn(returnId)
                .filter(request -> request.getParticipation().getUserId().equals(userId))
                .map(this::toLock);
    }

    public void approve(Long returnId, String approveType, LocalDateTime approvedAt) {
        ReturnRequest request = request(returnId);
        if (request.approve(approveType, approvedAt)) {
            request.getParticipation().markReturning();
        }
    }

    public void reject(Long returnId, String reason) {
        ReturnRequest request = request(returnId);
        if (request.reject(reason)) {
            request.getParticipation().releaseConfirmationHold();
        }
    }

    public void dispute(Long returnId) {
        ReturnRequest request = request(returnId);
        if (request.dispute()) {
            request.getParticipation().holdConfirmation();
        }
    }

    public Optional<ReturnLock> findAdminLock(Long returnId) {
        return findLockedReturn(returnId).map(this::toLock);
    }

    public boolean approveByAdmin(Long returnId, Long adminId, LocalDateTime resolvedAt) {
        ReturnRequest request = request(returnId);
        if (!request.approveByAdmin(adminId, resolvedAt)) {
            return false;
        }
        request.getParticipation().markReturning();
        return true;
    }

    public boolean rejectByAdmin(Long returnId, Long adminId, String reason,
                                 LocalDateTime resolvedAt) {
        ReturnRequest request = request(returnId);
        if (!request.rejectByAdmin(adminId, reason, resolvedAt)) {
            return false;
        }
        request.getParticipation().releaseConfirmationHold();
        return true;
    }

    public List<Long> findAutoApprovalCandidates(LocalDateTime now, int limit) {
        return requests.findAutoApprovalCandidates(now, PageRequest.of(0, limit));
    }

    public boolean approveAutomatically(Long returnId, LocalDateTime approvedAt) {
        Optional<ReturnRequest> found = findLockedReturn(returnId);
        if (found.isEmpty() || !found.get().approveAutomatically(approvedAt)) {
            return false;
        }
        found.get().getParticipation().markReturning();
        return true;
    }

    public Optional<RefundContext> findSellerRefundContext(Long sellerId, Long returnId) {
        return findLockedReturn(returnId)
                .filter(request -> groupBuy(request.getParticipation()).getSellerId().equals(sellerId))
                .flatMap(this::toRefundContext);
    }

    public Optional<RefundContext> findAutoRefundContext(Long returnId) {
        return findLockedReturn(returnId).flatMap(this::toRefundContext);
    }

    public List<Long> findAutoRefundCandidates(LocalDateTime now, int limit) {
        return requests.findAutoRefundCandidates(now, PageRequest.of(0, limit));
    }

    public boolean markRefundRequested(Long returnId, boolean automatic,
                                       LocalDateTime requestedAt) {
        ReturnRequest request = request(returnId);
        if (!request.requestRefund(automatic, requestedAt)) {
            return false;
        }
        request.getParticipation().markRefunding();
        return true;
    }

    public boolean markRefunded(Long returnId, LocalDateTime completedAt) {
        ReturnRequest request = lockedRequest(returnId);
        if (!request.completeRefund(completedAt)) {
            return false;
        }
        request.getParticipation().markRefunded();
        return true;
    }

    public boolean markRefundFailed(Long returnId) {
        return lockedRequest(returnId).failRefund();
    }

    private ReturnContext toContext(Participation participation) {
        GroupBuy groupBuy = groupBuys.findById(participation.getGroupBuyId())
                .orElseThrow(() -> new IllegalStateException("공동구매 정보를 찾을 수 없습니다."));
        Product product = product(groupBuy);
        return new ReturnContext(
                participation.getParticipationId(), participation.getGroupBuyId(),
                participation.getUserId(), groupBuy.getSellerId(), product.getName(),
                participation.getShareAmount(), participation.getShippingFee(),
                participation.getTotalAmount(), groupBuy.getReturnShippingFee(),
                participation.getStatus(), participation.getWithdrawalPeriodEndAt(),
                participation.getSettlementEligibleAt(), participation.isConfirmHold(),
                product.isApprovedWithdrawalRestriction());
    }

    private ReturnDetailResponse toDetail(ReturnRequest request) {
        return ReturnDetailResponse.from(
                toSummary(request), request.getPickupZipcode(), request.getPickupAddress1(),
                request.getPickupAddress2(), request.getRejectReason(),
                histories.findByReturnRequestReturnIdOrderByOccurredAtAscHistoryIdAsc(
                                request.getReturnId())
                        .stream()
                        .map(history -> new ReturnHistoryResponse(
                                history.getStatus(), history.getOccurredAt().atOffset(ZoneOffset.UTC),
                                history.getNote()))
                        .toList());
    }

    private ReturnSummaryResponse toSummary(ReturnRequest request) {
        Participation participation = request.getParticipation();
        GroupBuy groupBuy = groupBuy(participation);
        return new ReturnSummaryResponse(
                String.valueOf(request.getReturnId()), String.valueOf(participation.getParticipationId()),
                String.valueOf(participation.getGroupBuyId()), product(groupBuy).getName(),
                users.findById(participation.getUserId())
                        .orElseThrow(() -> new IllegalStateException("구매자 정보를 찾을 수 없습니다."))
                        .getNickname(), request.getReasonType(),
                request.getReasonDetail(), request.getStatus(), request.getRefundAmount(),
                request.getReturnShippingFee(), request.getFeeBearer(),
                request.getRequestedAt().atOffset(ZoneOffset.UTC),
                request.getAutoApproveAt() == null ? null
                        : request.getAutoApproveAt().atOffset(ZoneOffset.UTC),
                request.isPostConfirm(), request.isAdminReviewRequired());
    }

    private ReturnSummaryResponse toSummary(ReturnSummaryView view) {
        return new ReturnSummaryResponse(
                String.valueOf(view.getId()), String.valueOf(view.getParticipationId()),
                String.valueOf(view.getGroupBuyId()),
                view.getProductName(), view.getBuyerName(), view.getReasonType(),
                view.getDetailReason(), view.getStatus(), view.getRefundAmount(),
                view.getReturnShippingFee(), view.getShippingFeeBearer(),
                view.getRequestedAt().atOffset(ZoneOffset.UTC),
                view.getAutoApproveAt() == null ? null
                        : view.getAutoApproveAt().atOffset(ZoneOffset.UTC),
                view.getPostConfirm(), view.getAdminReviewRequired());
    }

    private Optional<RefundContext> toRefundContext(ReturnRequest request) {
        Participation participation = request.getParticipation();
        Optional<Payment> payment = payments.findByParticipationId(participation.getParticipationId());
        return payment.map(value -> new RefundContext(
                request.getReturnId(), participation.getParticipationId(),
                groupBuy(participation).getSellerId(), value.getPaymentId(), value.getMethod(),
                request.getRefundAmount(), request.getStatus(), request.getAutoRefundDueAt()));
    }

    private ReturnLock toLock(ReturnRequest request) {
        return new ReturnLock(
                request.getReturnId(), request.getParticipation().getParticipationId(),
                request.getStatus(), request.isAdminReviewRequired());
    }

    private ReturnRequest request(Long returnId) {
        return requests.findById(returnId)
                .orElseThrow(() -> new IllegalStateException("반품 정보를 찾을 수 없습니다."));
    }

    /** 확정/반품/정산은 참여 → 반품 순서로 잠가 교차 경합과 역순 교착을 피한다. */
    private Optional<ReturnRequest> findLockedReturn(Long returnId) {
        return requests.findParticipationId(returnId).flatMap(participationId -> {
            participations.findWithLockByParticipationId(participationId)
                    .orElseThrow(() -> new IllegalStateException("참여 정보를 찾을 수 없습니다."));
            return requests.findWithLockByReturnId(returnId);
        });
    }

    private ReturnRequest lockedRequest(Long returnId) {
        return findLockedReturn(returnId)
                .orElseThrow(() -> new IllegalStateException("반품 정보를 찾을 수 없습니다."));
    }

    private Participation participation(Long participationId) {
        return participations.findById(participationId)
                .orElseThrow(() -> new IllegalStateException("참여 정보를 찾을 수 없습니다."));
    }

    private GroupBuy groupBuy(Participation participation) {
        return groupBuys.findById(participation.getGroupBuyId())
                .orElseThrow(() -> new IllegalStateException("공동구매 정보를 찾을 수 없습니다."));
    }

    private Product product(GroupBuy groupBuy) {
        return products.findById(groupBuy.getProductId())
                .orElseThrow(() -> new IllegalStateException("상품 정보를 찾을 수 없습니다."));
    }

    public record ReturnContext(Long participationId, Long groupBuyId, Long userId,
                                Long sellerId, String productName, long shareAmount,
                                long originalShippingFee, long totalAmount,
                                long returnShippingFee, String participationStatus,
                                LocalDateTime withdrawalDeadline,
                                LocalDateTime settlementEligibleAt,
                                boolean confirmationHeld, boolean withdrawalRestricted) {
    }

    public record CreateCommand(Long participationId, String reasonType, String reasonDetail,
                                long returnShippingFee, String feeBearer, long refundAmount,
                                boolean postConfirm, boolean adminReviewRequired,
                                LocalDateTime requestedAt, LocalDateTime autoApproveAt,
                                String pickupZipcode, String pickupAddress1,
                                String pickupAddress2) {
    }

    public record ReturnLock(Long returnId, Long participationId, String status,
                             boolean adminReviewRequired) {
    }

    public record RefundContext(Long returnId, Long participationId, Long sellerId,
                                Long paymentId, String paymentMethod, long refundAmount,
                                String status, LocalDateTime autoRefundDueAt) {
    }
}
