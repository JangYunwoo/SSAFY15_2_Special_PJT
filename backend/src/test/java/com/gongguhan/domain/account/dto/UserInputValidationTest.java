package com.gongguhan.domain.account.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UserInputValidationTest {
    static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    static final Validator validator = factory.getValidator();
    @AfterAll static void closeFactory() { factory.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u00a0", "\u3000"})
    void rejectsSuppliedBlankNickname(String value) {
        assertThat(validator.validate(new UserUpdateRequest(value, null, null)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("nickname"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "   ", "1234567890", "0101234", "010123456789",
            "010-1234-5678", "0101234567a", "０１０１２３４５６７８", "01012345678\n"})
    void rejectsInvalidSuppliedPhoneAtBothProfileAndSignup(String value) {
        assertThat(validator.validate(new UserUpdateRequest(null, value, null)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
        var signup = new SignupRequest("validation@example.test", "test-only-password", "테스트", value,
                new SignupRequest.AddressRequest("테스트", "01012345678", "04524", "서울", null));
        assertThat(validator.validate(signup)).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    }

    @ParameterizedTest @NullSource
    @ValueSource(strings = {"01012345678", "021234567", "0212345678", "0311234567", "07012345678"})
    void preservesOptionalAndDomesticDigitsOnlyPhoneContract(String value) {
        assertThat(validator.validate(new UserUpdateRequest("정상 닉네임", value, null))).isEmpty();
    }

    @Test void nullFieldsMeanNoChangeAndExistingLengthAndAccountChecksRemain() {
        assertThat(validator.validate(new UserUpdateRequest(null, null, null))).isEmpty();
        assertThat(validator.validate(new UserUpdateRequest("가".repeat(51), null, null)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("nickname"));
        assertThat(validator.validate(new UserUpdateRequest("가".repeat(50), null, "1234567890"))).isEmpty();
        assertThat(validator.validate(new UserUpdateRequest(null, null, "abc")))
                .anyMatch(v -> v.getPropertyPath().toString().equals("ssafyAccountNo"));
    }
}
