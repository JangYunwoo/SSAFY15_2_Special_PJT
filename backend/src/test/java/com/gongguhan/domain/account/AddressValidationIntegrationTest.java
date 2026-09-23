package com.gongguhan.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.global.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:addressvalidation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false", "app.scheduling.enabled=false", "app.crypto.confirmation-poll-enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AddressValidationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserAddressRepository addresses;
    @Autowired JwtTokenProvider tokens;
    long userId;
    long primaryId;
    long otherId;
    String token;

    @BeforeEach void fixture() {
        userId = users.saveAndFlush(User.create("address-" + UUID.randomUUID() + "@example.test",
                "not-a-login-hash", "테스트", "01012345678")).getUserId();
        primaryId = addresses.saveAndFlush(UserAddress.create(userId, "원래이름", "01012345678", "04524", "서울", "101호", "문 앞", true)).getAddressId();
        otherId = addresses.saveAndFlush(UserAddress.create(userId, "수정대상", "0212345678", "04524", "서울", null, null, false)).getAddressId();
        token = tokens.createAccessToken(userId, "USER", false);
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "0101234", "010-1234-5678"})
    void rejectsCreateWithoutInsertingOrReplacingDefault(String phone) throws Exception {
        mvc.perform(post("/users/me/addresses").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientName\":\"테스트\",\"recipientPhone\":\"" + phone
                                + "\",\"zipcode\":\"04524\",\"address1\":\"서울\",\"isDefault\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(addresses.findByUserIdOrderByIsDefaultDescAddressIdAsc(userId)).hasSize(2);
        assertThat(addresses.findById(primaryId).orElseThrow().isDefault()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "", "0101234"})
    void rejectsPatchWithoutAnyPartialWrite(String phone) throws Exception {
        mvc.perform(patch("/users/me/addresses/" + otherId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientName\":\"변경금지\",\"recipientPhone\":\"" + phone + "\",\"isDefault\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        UserAddress other = addresses.findById(otherId).orElseThrow();
        assertThat(other.getRecipientName()).isEqualTo("수정대상");
        assertThat(other.getRecipientPhone()).isEqualTo("0212345678");
        assertThat(other.isDefault()).isFalse();
        assertThat(addresses.findById(primaryId).orElseThrow().isDefault()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"01012345678", "021234567", "07012345678"})
    void acceptsValidCreateAndPatch(String phone) throws Exception {
        mvc.perform(post("/users/me/addresses").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientName\":\"테스트\",\"recipientPhone\":\"" + phone + "\",\"zipcode\":\"04524\",\"address1\":\"서울\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.recipientPhone").value(phone));
        mvc.perform(patch("/users/me/addresses/" + otherId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientPhone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        assertThat(addresses.findById(otherId).orElseThrow().getRecipientPhone()).isEqualTo(phone);
    }

    @Test void omittedAndNullPhonePreserveValueAndDeliveryRequestNullStillClears() throws Exception {
        mvc.perform(patch("/users/me/addresses/" + primaryId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientName\":\"새 이름\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.recipientPhone").value("01012345678"));
        mvc.perform(patch("/users/me/addresses/" + primaryId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientPhone\":null,\"deliveryRequest\":null}"))
                .andExpect(status().isOk());
        UserAddress result = addresses.findById(primaryId).orElseThrow();
        assertThat(result.getRecipientPhone()).isEqualTo("01012345678");
        assertThat(result.getRecipientName()).isEqualTo("새 이름");
        assertThat(result.getDeliveryRequest()).isNull();
    }
}
