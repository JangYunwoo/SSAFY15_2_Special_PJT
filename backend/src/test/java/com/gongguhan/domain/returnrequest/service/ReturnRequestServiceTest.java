package com.gongguhan.domain.returnrequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReturnRequestServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long SELLER_ID = 20L;
    private static final Long PARTICIPATION_ID = 30L;
    private static final Long RETURN_ID = 40L;
    private static final Long ADMIN_ID = 50L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 5, 0);

    private ReturnRequestRepository repository;
    private ReturnRequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReturnRequestRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-11T05:00:00Z"), ZoneOffset.UTC);
        service = new ReturnRequestService(repository, new ReturnBusinessDayCalculator(), clock);
    }

    @Test
    void changeOfMindPreviewMatchesSpecifiedExample() {
        when(repository.findContext(PARTICIPATION_ID, false))
                .thenReturn(Optional.of(context("DELIVERED", false, false)));

        ReturnPreviewResponse result = service.preview(USER_ID, PARTICIPATION_ID,
                new ReturnPreviewRequest("CHANGE_OF_MIND", "단순 변심"));

        assertThat(result.eligible()).isTrue();
        assertThat(result.paymentAmount()).isEqualTo(53_000);
        assertThat(result.refundableShareAmount()).isEqualTo(50_000);
        assertThat(result.refundableOriginalShippingFee()).isZero();
        assertThat(result.returnShippingFee()).isEqualTo(6_000);
        assertThat(result.refundAmount()).isEqualTo(44_000);
        assertThat(result.shippingFeeBearer()).isEqualTo("BUYER");
    }

    @Test
    void defectRefundsEntirePaymentAndSellerBearsReturnFee() {
        when(repository.findContext(PARTICIPATION_ID, false))
                .thenReturn(Optional.of(context("DELIVERED", true, false)));

        ReturnPreviewResponse result = service.preview(USER_ID, PARTICIPATION_ID,
                new ReturnPreviewRequest("DEFECT", "파손"));

        assertThat(result.eligible()).isTrue();
        assertThat(result.refundableOriginalShippingFee()).isEqualTo(3_000);
        assertThat(result.refundAmount()).isEqualTo(53_000);
        assertThat(result.shippingFeeBearer()).isEqualTo("SELLER");
    }

    @Test
    void restrictedProductAllowsOnlyDefectReason() {
        when(repository.findContext(PARTICIPATION_ID, false))
                .thenReturn(Optional.of(context("DELIVERED", true, false)));

        ReturnPreviewResponse result = service.preview(USER_ID, PARTICIPATION_ID,
                new ReturnPreviewRequest("CHANGE_OF_MIND", null));

        assertThat(result.eligible()).isFalse();
        assertThat(result.ineligibleReason()).contains("하자 사유로만");
    }

    @Test
    void requestBeforeDeliveryIsRejected() {
        when(repository.findContext(PARTICIPATION_ID, true))
                .thenReturn(Optional.of(context("SHIPPING", false, false)));

        assertThatThrownBy(() -> service.create(USER_ID, PARTICIPATION_ID, createRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RETURN_NOT_ELIGIBLE));
        verify(repository, never()).create(any());
    }

    @Test
    void createsReturnAndHoldsPurchaseConfirmation() {
        when(repository.findContext(PARTICIPATION_ID, true))
                .thenReturn(Optional.of(context("DELIVERED", false, false)));
        when(repository.create(any())).thenReturn(RETURN_ID);
        when(repository.findBuyerDetail(USER_ID, RETURN_ID)).thenReturn(Optional.of(detail("REQUESTED")));

        service.create(USER_ID, PARTICIPATION_ID, createRequest());

        ArgumentCaptor<CreateCommand> command = ArgumentCaptor.forClass(CreateCommand.class);
        verify(repository).create(command.capture());
        assertThat(command.getValue().autoApproveAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 16, 5, 0));
        assertThat(command.getValue().postConfirm()).isFalse();
        verify(repository).holdConfirmation(PARTICIPATION_ID);
        verify(repository).addHistory(RETURN_ID, "REQUESTED", null, NOW);
    }

    @Test
    void confirmedOrderBecomesAdminReviewWithoutAutoApproval() {
        when(repository.findContext(PARTICIPATION_ID, true))
                .thenReturn(Optional.of(context("CONFIRMED", false, false)));
        when(repository.create(any())).thenReturn(RETURN_ID);
        when(repository.findBuyerDetail(USER_ID, RETURN_ID)).thenReturn(Optional.of(detail("REQUESTED")));

        service.create(USER_ID, PARTICIPATION_ID, createRequest());

        ArgumentCaptor<CreateCommand> command = ArgumentCaptor.forClass(CreateCommand.class);
        verify(repository).create(command.capture());
        assertThat(command.getValue().postConfirm()).isTrue();
        assertThat(command.getValue().adminReviewRequired()).isTrue();
        assertThat(command.getValue().autoApproveAt()).isNull();
    }

    @Test
    void rejectsDuplicateOpenReturn() {
        when(repository.findContext(PARTICIPATION_ID, true))
                .thenReturn(Optional.of(context("DELIVERED", false, false)));
        when(repository.existsOpenReturn(PARTICIPATION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER_ID, PARTICIPATION_ID, createRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.PURCHASE_CONFIRM_RETURN_CONFLICT));
    }

    @Test
    void sellerApprovalMovesRequestToApproved() {
        when(repository.findSellerLock(SELLER_ID, RETURN_ID))
                .thenReturn(Optional.of(new ReturnLock(RETURN_ID, PARTICIPATION_ID,
                        "REQUESTED", false)));
        when(repository.findSellerDetail(SELLER_ID, RETURN_ID))
                .thenReturn(Optional.of(detail("APPROVED")));

        ReturnDetailResponse result = service.approve(SELLER_ID, RETURN_ID);

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(repository).approve(RETURN_ID, "MANUAL", NOW);
        verify(repository).addHistory(RETURN_ID, "APPROVED", "판매자가 반품을 승인했습니다.", NOW);
    }

    @Test
    void sellerCannotApprovePostConfirmReview() {
        when(repository.findSellerLock(SELLER_ID, RETURN_ID))
                .thenReturn(Optional.of(new ReturnLock(RETURN_ID, PARTICIPATION_ID,
                        "REQUESTED", true)));

        assertThatThrownBy(() -> service.approve(SELLER_ID, RETURN_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        verify(repository, never()).approve(any(), any(), any());
    }

    @Test
    void buyerCanDisputeOnlyRejectedReturn() {
        when(repository.findBuyerLock(USER_ID, RETURN_ID))
                .thenReturn(Optional.of(new ReturnLock(RETURN_ID, PARTICIPATION_ID,
                        "REJECTED", false)));

        service.dispute(USER_ID, RETURN_ID, "판정 재검토 요청");

        verify(repository).dispute(RETURN_ID);
        verify(repository).addHistory(RETURN_ID, "DISPUTED", "판정 재검토 요청", NOW);
    }

    @Test
    void adminCanApproveDisputedReturn() {
        when(repository.findAdminLock(RETURN_ID))
                .thenReturn(Optional.of(new ReturnLock(RETURN_ID, PARTICIPATION_ID,
                        "DISPUTED", true)));
        when(repository.findAdminDetail(RETURN_ID)).thenReturn(Optional.of(detail("APPROVED")));

        ReturnDetailResponse result = service.resolveByAdmin(
                ADMIN_ID, RETURN_ID, "APPROVE", "하자 증빙 확인");

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(repository).approveByAdmin(RETURN_ID, ADMIN_ID, NOW);
        verify(repository).addHistory(RETURN_ID, "APPROVED", "하자 증빙 확인", NOW);
    }

    @Test
    void adminRejectRequiresReason() {
        assertThatThrownBy(() -> service.resolveByAdmin(
                ADMIN_ID, RETURN_ID, "REJECT", "  "))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(repository, never()).findAdminLock(any());
    }

    @Test
    void adminCannotResolveReviewThatAlreadyEnded() {
        when(repository.findAdminLock(RETURN_ID))
                .thenReturn(Optional.of(new ReturnLock(RETURN_ID, PARTICIPATION_ID,
                        "APPROVED", false)));

        assertThatThrownBy(() -> service.resolveByAdmin(
                ADMIN_ID, RETURN_ID, "APPROVE", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        verify(repository, never()).approveByAdmin(any(), any(), any());
    }

    @Test
    void automaticApprovalWritesHistoryOnlyAfterConditionalUpdate() {
        when(repository.approveAutomatically(RETURN_ID, NOW)).thenReturn(true);

        assertThat(service.approveAutomatically(RETURN_ID)).isTrue();
        verify(repository).addHistory(
                RETURN_ID, "APPROVED", "판매자 무응답으로 자동 승인되었습니다.", NOW);
    }

    private ReturnCreateRequest createRequest() {
        return new ReturnCreateRequest("CHANGE_OF_MIND", "단순 변심",
                "06236", "서울시 강남구", "101호");
    }

    private ReturnContext context(String status, boolean restricted, boolean held) {
        return new ReturnContext(PARTICIPATION_ID, 31L, USER_ID, SELLER_ID, "상품",
                50_000, 3_000, 53_000, 6_000, status,
                NOW.plusDays(7), NOW.plusDays(8), held, restricted);
    }

    private ReturnDetailResponse detail(String status) {
        ReturnSummaryResponse summary = new ReturnSummaryResponse(
                RETURN_ID, PARTICIPATION_ID, 31L, "상품", "구매자",
                "CHANGE_OF_MIND", "단순 변심", status, 44_000, 6_000,
                "BUYER", OffsetDateTime.of(NOW, ZoneOffset.UTC), null, false, false);
        return ReturnDetailResponse.from(summary, "06236", "서울시 강남구", "101호", null, List.of());
    }
}
