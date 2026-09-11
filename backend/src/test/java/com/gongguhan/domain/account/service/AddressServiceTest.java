package com.gongguhan.domain.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.dto.AddressCreateRequest;
import com.gongguhan.domain.account.dto.AddressResponse;
import com.gongguhan.domain.account.dto.AddressUpdateRequest;
import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ADDRESS_ID = 10L;

    private UserAddressRepository userAddressRepository;
    private AddressService addressService;

    @BeforeEach
    void setUp() {
        userAddressRepository = mock(UserAddressRepository.class);
        addressService = new AddressService(userAddressRepository);
    }

    private UserAddress defaultAddress() {
        return UserAddress.createDefault(USER_ID, "홍길동", "01012345678", "06236", "서울시 강남구", "101호");
    }

    private AddressCreateRequest createRequest(Boolean isDefault) {
        return new AddressCreateRequest(
                "김철수", "01098765432", "04524", "서울시 중구", "202호", "문 앞에 놓아주세요", isDefault);
    }

    @Test
    void firstAddressBecomesDefaultEvenWithoutFlag() {
        when(userAddressRepository.findByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.empty());

        AddressResponse response = addressService.create(USER_ID, createRequest(null));

        assertTrue(response.isDefault());
    }

    @Test
    void newDefaultClearsExistingDefault() {
        UserAddress existing = defaultAddress();
        when(userAddressRepository.findByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(existing));

        AddressResponse response = addressService.create(USER_ID, createRequest(true));

        assertTrue(response.isDefault());
        assertFalse(existing.isDefault());
    }

    @Test
    void nonDefaultCreateKeepsExistingDefault() {
        UserAddress existing = defaultAddress();
        when(userAddressRepository.findByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(existing));

        AddressResponse response = addressService.create(USER_ID, createRequest(false));

        assertFalse(response.isDefault());
        assertTrue(existing.isDefault());
    }

    @Test
    void promotingAddressToDefaultClearsPreviousOne() {
        UserAddress previousDefault = defaultAddress();
        UserAddress target = UserAddress.create(
                USER_ID, "김철수", "01098765432", "04524", "서울시 중구", "202호", null, false);
        when(userAddressRepository.findByAddressIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(target));
        when(userAddressRepository.findByUserIdAndIsDefaultTrue(USER_ID))
                .thenReturn(Optional.of(previousDefault));

        AddressResponse response = addressService.update(USER_ID, ADDRESS_ID,
                new AddressUpdateRequest(null, null, null, null, null, null, true));

        assertTrue(response.isDefault());
        assertFalse(previousDefault.isDefault());
    }

    @Test
    void deletingDefaultPromotesRemainingAddress() {
        UserAddress target = defaultAddress();
        UserAddress remaining = UserAddress.create(
                USER_ID, "김철수", "01098765432", "04524", "서울시 중구", "202호", null, false);
        when(userAddressRepository.findByAddressIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(target));
        when(userAddressRepository.findByUserIdOrderByIsDefaultDescAddressIdAsc(USER_ID))
                .thenReturn(List.of(remaining));

        addressService.delete(USER_ID, ADDRESS_ID);

        verify(userAddressRepository).delete(target);
        assertTrue(remaining.isDefault());
    }

    @Test
    void updateDeniedForNotOwnedAddress() {
        when(userAddressRepository.findByAddressIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> addressService.update(USER_ID, ADDRESS_ID,
                        new AddressUpdateRequest(null, null, null, null, null, null, null)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(userAddressRepository, org.mockito.Mockito.never()).save(any());
    }
}
