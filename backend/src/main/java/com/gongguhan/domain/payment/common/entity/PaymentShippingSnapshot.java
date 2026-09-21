package com.gongguhan.domain.payment.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 주소록과 생명주기가 다른 거래 시점의 배송 정보. 생성 후 수정하지 않는다. */
@Embeddable
public class PaymentShippingSnapshot {
    @Column(name = "shipping_recipient_name", length = 50)
    private String recipientName;
    @Column(name = "shipping_recipient_phone", length = 20)
    private String recipientPhone;
    @Column(name = "shipping_zipcode", length = 5)
    private String zipcode;
    @Column(name = "shipping_address1")
    private String address1;
    @Column(name = "shipping_address2")
    private String address2;
    @Column(name = "shipping_delivery_request", length = 100)
    private String deliveryRequest;

    protected PaymentShippingSnapshot() {}

    public PaymentShippingSnapshot(String recipientName, String recipientPhone, String zipcode,
            String address1, String address2, String deliveryRequest) {
        this.recipientName = java.util.Objects.requireNonNull(recipientName);
        this.recipientPhone = java.util.Objects.requireNonNull(recipientPhone);
        this.zipcode = java.util.Objects.requireNonNull(zipcode);
        this.address1 = java.util.Objects.requireNonNull(address1);
        this.address2 = address2;
        this.deliveryRequest = deliveryRequest;
    }

    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getZipcode() { return zipcode; }
    public String getAddress1() { return address1; }
    public String getAddress2() { return address2; }
    public String getDeliveryRequest() { return deliveryRequest; }
}
