package com.gongguhan.domain.account.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.account.dto.AddressUpdateRequest;
import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import jakarta.validation.Validation;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AddressRequestClearTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    private UserAddress apply(String json) {
        UserAddress address = UserAddress.create(1L, "테스트", "01012345678", "04524", "서울", "101호", "이전 요청", true);
        UserAddressRepository repository = mock(UserAddressRepository.class);
        when(repository.findByAddressIdAndUserId(10L, 1L)).thenReturn(Optional.of(address));
        new AddressService(repository).update(1L, 10L, mapper.readValue(json, AddressUpdateRequest.class));
        return address;
    }

    @Test void omittedRequestRemainsUnchanged() {
        UserAddress result = apply("{\"recipientName\":\"변경 이름\"}");
        assertEquals("이전 요청", result.getDeliveryRequest());
        assertEquals("변경 이름", result.getRecipientName());
    }

    @Test void explicitNullClearsRequest() {
        assertNull(apply("{\"deliveryRequest\":null}").getDeliveryRequest());
    }

    @Test void explicitValueReplacesRequest() {
        assertEquals("경비실", apply("{\"deliveryRequest\":\"경비실\"}").getDeliveryRequest());
    }

    @Test void absentOtherFieldsAreNotErased() {
        UserAddress result = apply("{\"deliveryRequest\":null}");
        assertEquals("서울", result.getAddress1());
        assertEquals("101호", result.getAddress2());
        assertTrue(result.isDefault());
    }

    @Test void presenceFlagCannotBeSuppliedByClient() {
        assertFalse(mapper.readValue("{\"deliveryRequestPresent\":true}", AddressUpdateRequest.class).hasDeliveryRequest());
    }

    @Test void requestStillHasLengthValidation() {
        AddressUpdateRequest request = mapper.readValue("{\"deliveryRequest\":\"" + "가".repeat(101) + "\"}", AddressUpdateRequest.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("deliveryRequest")));
        }
    }
}
