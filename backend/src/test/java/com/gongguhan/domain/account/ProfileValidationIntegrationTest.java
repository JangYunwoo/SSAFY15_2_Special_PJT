package com.gongguhan.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
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

/** Real HTTP validation, authentication, service and isolated persistence; no financial calls. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:profilevalidation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false", "app.scheduling.enabled=false", "app.crypto.confirmation-poll-enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ProfileValidationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    long id;
    String token;

    @BeforeEach void fixture() {
        User user = users.saveAndFlush(User.create("validation-" + UUID.randomUUID() + "@example.test",
                "not-a-login-hash", "원래이름", "01012345678"));
        id = user.getUserId();
        token = tokens.createAccessToken(id, "USER", false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"nickname\":\"\"}", "{\"nickname\":\"  \"}",
            "{\"phone\":\"abc\"}", "{\"phone\":\"\"}", "{\"nickname\":\"변경금지\",\"phone\":\"abc\"}"})
    void rejectsBadInputWithoutChangingPersistedProfile(String json) throws Exception {
        mvc.perform(patch("/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors").isNotEmpty());
        User user = users.findById(id).orElseThrow();
        assertThat(user.getNickname()).isEqualTo("원래이름");
        assertThat(user.getPhone()).isEqualTo("01012345678");
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"nickname\":null,\"phone\":null}"})
    void preservesOmittedAndNullFields(String json) throws Exception {
        mvc.perform(patch("/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("원래이름"))
                .andExpect(jsonPath("$.data.phone").value("01012345678"));
    }

    @Test void permitsNicknameOnlyThenPhoneOnlyUpdates() throws Exception {
        mvc.perform(patch("/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"새 이름\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("01012345678"));
        mvc.perform(patch("/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"021234567\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("새 이름"));
        assertThat(users.findById(id).orElseThrow().getPhone()).isEqualTo("021234567");
    }
}
