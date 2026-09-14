package com.gongguhan.domain.account.entity;

import com.gongguhan.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_addresses")
public class UserAddress extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 50)
    private String alias;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 5)
    private String zipcode;

    @Column(nullable = false)
    private String address1;

    private String address2;

    @Column(name = "delivery_request", length = 100)
    private String deliveryRequest;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    protected UserAddress() {
    }

    private UserAddress(Long userId, String recipientName, String recipientPhone,
                         String zipcode, String address1, String address2, boolean isDefault) {
        this.userId = userId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
        this.isDefault = isDefault;
    }

    public static UserAddress createDefault(Long userId, String recipientName, String recipientPhone,
                                             String zipcode, String address1, String address2) {
        return new UserAddress(userId, recipientName, recipientPhone, zipcode, address1, address2, true);
    }

    public static UserAddress create(Long userId, String recipientName, String recipientPhone,
                                      String zipcode, String address1, String address2,
                                      String deliveryRequest, boolean isDefault) {
        UserAddress address = new UserAddress(
                userId, recipientName, recipientPhone, zipcode, address1, address2, isDefault);
        address.deliveryRequest = deliveryRequest;
        return address;
    }

    /** null 필드는 유지하는 부분 수정 (USR-05 PATCH 시맨틱). isDefault 승격은 서비스가 단일성 보장 후 호출한다. */
    public void update(String recipientName, String recipientPhone, String zipcode,
                        String address1, String address2, String deliveryRequest) {
        if (recipientName != null) {
            this.recipientName = recipientName;
        }
        if (recipientPhone != null) {
            this.recipientPhone = recipientPhone;
        }
        if (zipcode != null) {
            this.zipcode = zipcode;
        }
        if (address1 != null) {
            this.address1 = address1;
        }
        if (address2 != null) {
            this.address2 = address2;
        }
        if (deliveryRequest != null) {
            this.deliveryRequest = deliveryRequest;
        }
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void clearDefault() {
        this.isDefault = false;
    }

    public Long getAddressId() {
        return addressId;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getZipcode() {
        return zipcode;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public String getDeliveryRequest() {
        return deliveryRequest;
    }
}
