package com.gongguhan.domain.account.service;

import com.gongguhan.domain.account.dto.SellerProfileResponse;
import com.gongguhan.domain.account.dto.SellerProfileUpdateRequest;
import com.gongguhan.domain.account.entity.SellerProfile;
import com.gongguhan.domain.account.repository.SellerProfileRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자 프로필 조회·수정 (SEL-06·07, S15P21B205-63). */
@Service
@Transactional(readOnly = true)
public class SellerProfileService {

    private final SellerProfileRepository sellerProfileRepository;

    public SellerProfileService(SellerProfileRepository sellerProfileRepository) {
        this.sellerProfileRepository = sellerProfileRepository;
    }

    public SellerProfileResponse getProfile(Long sellerId) {
        return SellerProfileResponse.from(findProfile(sellerId));
    }

    @Transactional
    public SellerProfileResponse updateProfile(Long sellerId, SellerProfileUpdateRequest request) {
        SellerProfile profile = findProfile(sellerId);
        profile.update(request.businessPhone(), request.ssafyAccountNo(), request.accountHolder(),
                request.returnZipcode(), request.returnAddress1(), request.returnAddress2());
        return SellerProfileResponse.from(profile);
    }

    private SellerProfile findProfile(Long sellerId) {
        return sellerProfileRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "판매자 프로필을 찾을 수 없습니다."));
    }
}
