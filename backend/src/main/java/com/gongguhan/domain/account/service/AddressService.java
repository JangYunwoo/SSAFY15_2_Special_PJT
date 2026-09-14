package com.gongguhan.domain.account.service;

import com.gongguhan.domain.account.dto.AddressCreateRequest;
import com.gongguhan.domain.account.dto.AddressResponse;
import com.gongguhan.domain.account.dto.AddressUpdateRequest;
import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송지 CRUD (USR-03~06, S15P21B205-63).
 *
 * <p>기본 배송지 단일성: 새 기본 배송지를 지정하면 같은 트랜잭션에서 기존 기본을
 * 해제한다. 사용자당 배송지 수가 적어(수 개) 조회·해제 두 번의 쿼리로 충분하다.
 */
@Service
@Transactional(readOnly = true)
public class AddressService {

    private final UserAddressRepository userAddressRepository;

    public AddressService(UserAddressRepository userAddressRepository) {
        this.userAddressRepository = userAddressRepository;
    }

    public List<AddressResponse> getAddresses(Long userId) {
        return userAddressRepository.findByUserIdOrderByIsDefaultDescAddressIdAsc(userId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse create(Long userId, AddressCreateRequest request) {
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault())
                || userAddressRepository.findByUserIdAndIsDefaultTrue(userId).isEmpty();
        if (makeDefault) {
            clearExistingDefault(userId);
        }
        UserAddress address = UserAddress.create(
                userId, request.recipientName(), request.recipientPhone(), request.zipcode(),
                request.address1(), request.address2(), request.deliveryRequest(), makeDefault);
        userAddressRepository.save(address);
        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressUpdateRequest request) {
        UserAddress address = findOwned(userId, addressId);
        address.update(request.recipientName(), request.recipientPhone(), request.zipcode(),
                request.address1(), request.address2(), request.deliveryRequest());
        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            clearExistingDefault(userId);
            address.markDefault();
        }
        return AddressResponse.from(address);
    }

    /**
     * 기본 배송지도 삭제할 수 있다 - 결제·참여는 주소 스냅샷을 복사해 두므로 과거
     * 거래에는 영향이 없고, 남은 배송지가 있으면 가장 오래된 것을 기본으로 승격한다.
     */
    @Transactional
    public void delete(Long userId, Long addressId) {
        UserAddress address = findOwned(userId, addressId);
        boolean wasDefault = address.isDefault();
        userAddressRepository.delete(address);
        if (wasDefault) {
            userAddressRepository.findByUserIdOrderByIsDefaultDescAddressIdAsc(userId).stream()
                    .findFirst()
                    .ifPresent(UserAddress::markDefault);
        }
    }

    private void clearExistingDefault(Long userId) {
        userAddressRepository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(UserAddress::clearDefault);
    }

    private UserAddress findOwned(Long userId, Long addressId) {
        return userAddressRepository.findByAddressIdAndUserId(addressId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "배송지를 찾을 수 없습니다."));
    }
}
