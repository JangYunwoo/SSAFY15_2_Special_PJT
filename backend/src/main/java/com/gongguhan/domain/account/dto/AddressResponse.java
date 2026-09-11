package com.gongguhan.domain.account.dto;

import com.gongguhan.domain.account.entity.UserAddress;

/** USR-03~05 응답. 필드명은 FE 계약(entities/address/model.ts의 Address)과 1:1. */
public record AddressResponse(
        String id,
        String recipientName,
        String recipientPhone,
        String zipcode,
        String address1,
        String address2,
        boolean isDefault,
        String deliveryRequest
) {

    public static AddressResponse from(UserAddress address) {
        return new AddressResponse(
                String.valueOf(address.getAddressId()),
                address.getRecipientName(),
                address.getRecipientPhone(),
                address.getZipcode(),
                address.getAddress1(),
                address.getAddress2(),
                address.isDefault(),
                address.getDeliveryRequest());
    }
}
