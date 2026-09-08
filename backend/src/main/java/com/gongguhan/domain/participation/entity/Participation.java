package com.gongguhan.domain.participation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "participations")
public class Participation {

    public static final String STATUS_PAID = "PAID";
    public static final String ACTIVE_MARKER = "Y";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participation_id")
    private Long participationId;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "share_amount", nullable = false)
    private long shareAmount;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 5)
    private String zipcode;

    @Column(nullable = false)
    private String address1;

    private String address2;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "active_marker", length = 1)
    private String activeMarker;

    protected Participation() {
    }

    private Participation(Long groupBuyId, Long userId, long shareAmount, long shippingFee, long totalAmount,
                           String recipientName, String recipientPhone, String zipcode,
                           String address1, String address2) {
        this.groupBuyId = groupBuyId;
        this.userId = userId;
        this.shareAmount = shareAmount;
        this.shippingFee = shippingFee;
        this.totalAmount = totalAmount;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
        this.status = STATUS_PAID;
        this.joinedAt = LocalDateTime.now();
        this.activeMarker = ACTIVE_MARKER;
    }

    public static Participation create(Long groupBuyId, Long userId, long shareAmount, long shippingFee,
                                        long totalAmount, String recipientName, String recipientPhone,
                                        String zipcode, String address1, String address2) {
        return new Participation(groupBuyId, userId, shareAmount, shippingFee, totalAmount,
                recipientName, recipientPhone, zipcode, address1, address2);
    }

    public Long getParticipationId() {
        return participationId;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getUserId() {
        return userId;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public String getActiveMarker() {
        return activeMarker;
    }
}
