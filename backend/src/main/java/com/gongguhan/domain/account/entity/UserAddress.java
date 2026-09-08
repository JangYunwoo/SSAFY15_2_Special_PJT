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

    public Long getAddressId() {
        return addressId;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
