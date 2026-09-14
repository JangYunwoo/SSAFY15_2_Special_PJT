package com.gongguhan.domain.account.dto;

import com.gongguhan.domain.account.entity.SellerProfile;

/** SEL-06 판매자 프로필·제재 상태 응답. */
public record SellerProfileResponse(
        String id,
        String businessName,
        String businessPhone,
        String ssafyAccountNo,
        String accountHolder,
        boolean accountVerified,
        String returnZipcode,
        String returnAddress1,
        String returnAddress2,
        int penaltyCount,
        String status
) {

    public static SellerProfileResponse from(SellerProfile profile) {
        return new SellerProfileResponse(
                String.valueOf(profile.getSellerId()),
                profile.getBusinessName(),
                profile.getBusinessPhone(),
                profile.getSsafyAccountNo(),
                profile.getAccountHolder(),
                profile.isAccountVerified(),
                profile.getReturnZipcode(),
                profile.getReturnAddress1(),
                profile.getReturnAddress2(),
                profile.getPenaltyCount(),
                profile.getStatus());
    }
}
