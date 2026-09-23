package com.gongguhan.domain.account.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressInputValidationTest {
    static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    static final Validator validator = factory.getValidator();
    @AfterAll static void closeFactory() { factory.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0101234", "010123456789", "1234567890", "010-1234-5678", "０１０１２３４５６７８"})
    void rejectsBadPhoneAcrossCreateUpdateAndSignupDefaultAddress(String phone) {
        assertThat(validator.validate(create(phone))).anyMatch(v -> v.getPropertyPath().toString().equals("recipientPhone"));
        assertThat(validator.validate(update(phone))).anyMatch(v -> v.getPropertyPath().toString().equals("recipientPhone"));
        var signup = new SignupRequest("address@example.test", "test-only-password", "테스트", null,
                new SignupRequest.AddressRequest("테스트", phone, "04524", "서울", null));
        assertThat(validator.validate(signup)).anyMatch(v -> v.getPropertyPath().toString().equals("defaultAddress.recipientPhone"));
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "\t"})
    void patchCannotClearRequiredPhoneWithEmptyValue(String phone) {
        assertThat(validator.validate(update(phone))).anyMatch(v -> v.getPropertyPath().toString().equals("recipientPhone"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"01012345678", "021234567", "0212345678", "0311234567", "07012345678"})
    void validDomesticPhonesRemainAccepted(String phone) {
        assertThat(validator.validate(create(phone))).isEmpty();
        assertThat(validator.validate(update(phone))).isEmpty();
    }

    @Test void nullMeansUnchangedOnlyForPatch() {
        assertThat(validator.validate(update(null))).isEmpty();
        assertThat(validator.validate(create(null))).anyMatch(v -> v.getPropertyPath().toString().equals("recipientPhone"));
    }

    private AddressCreateRequest create(String phone) {
        return new AddressCreateRequest("테스트", phone, "04524", "서울", null, null, false);
    }
    private AddressUpdateRequest update(String phone) {
        return new AddressUpdateRequest(null, phone, null, null, null, null, null);
    }
}
