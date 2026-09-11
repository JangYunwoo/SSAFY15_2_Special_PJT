package com.gongguhan.domain.delivery.entity;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 참여자별 배송 (READY → SHIPPING → DELIVERED).
 *
 * <p>행 생성은 모집 완료 시점에 일괄로 일어난다
 * ({@link com.gongguhan.domain.delivery.service.DeliveryProvisioningListener}) -
 * 송장 등록(DLV-02)은 이미 존재하는 행에 값을 채우는 것이지 행을 만들지 않는다.
 */
@Entity
@Table(name = "deliveries")
public class Delivery {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_SHIPPING = "SHIPPING";
    public static final String STATUS_DELIVERED = "DELIVERED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id")
    private Long deliveryId;

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    @Column(name = "courier_code", length = 20)
    private String courierCode;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Delivery() {
    }

    private Delivery(Long participationId) {
        this.participationId = participationId;
        this.status = STATUS_READY;
    }

    public static Delivery ready(Long participationId) {
        return new Delivery(participationId);
    }

    /**
     * 송장 등록·수정(DLV-02). PUT 시맨틱대로 READY 동안에는 재등록으로 값을 교체할 수
     * 있지만, 발송 처리(SHIPPING) 이후에는 배송 조회 근거가 바뀌므로 막는다.
     */
    public void registerInvoice(String courierCode, String trackingNumber, LocalDateTime shippedAt) {
        if (!isReady()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "발송 처리 전에만 송장을 등록·수정할 수 있습니다.");
        }
        this.courierCode = courierCode;
        this.trackingNumber = trackingNumber;
        this.shippedAt = shippedAt;
    }

    public void markShipping() {
        if (!isReady()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "발송 대기(READY) 상태에서만 발송 처리할 수 있습니다.");
        }
        if (trackingNumber == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "송장 등록 후에 발송 처리할 수 있습니다.");
        }
        this.status = STATUS_SHIPPING;
    }

    public void markDelivered(LocalDateTime deliveredAt) {
        if (!isShipping()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "배송 중(SHIPPING) 상태에서만 배송 완료 처리할 수 있습니다.");
        }
        this.status = STATUS_DELIVERED;
        this.deliveredAt = deliveredAt;
    }

    public boolean isReady() {
        return STATUS_READY.equals(status);
    }

    public boolean isShipping() {
        return STATUS_SHIPPING.equals(status);
    }

    public boolean hasInvoice() {
        return trackingNumber != null;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public String getCourierCode() {
        return courierCode;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public String getStatus() {
        return status;
    }
}
