package com.gongguhan.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 서명 JWT, 필터 체인, 컨트롤러, 격리 DB를 통과시킨다. 요청 토큰 출력은 금지한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securityboundary;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ActorBoundaryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    private long sharedId;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void createDifferentActorsWithSameNumericId() {
        User user = users.saveAndFlush(User.create("boundary-" + UUID.randomUUID() + "@example.test",
                "not-a-login-hash", "원래구매자", "01000000000"));
        sharedId = user.getUserId();
        jdbc.update("INSERT INTO admins(admin_id,email,password_hash,name,role,status,created_at) VALUES(?,?,?,'QA','ADMIN','ACTIVE',NOW())",
                sharedId, "admin-" + UUID.randomUUID() + "@example.test", "not-a-login-hash");
        adminToken = tokens.createAccessToken(sharedId, "ADMIN", false);
        userToken = tokens.createAccessToken(sharedId, "USER", false);
    }

    @Test void adminMustNotReadBuyerWithSameId() throws Exception {
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ROLE_ACCESS_DENIED"));
    }

    @Test void adminMustNotModifyBuyerWithSameId() throws Exception {
        mvc.perform(patch("/users/me").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"변경금지\"}"))
                .andExpect(status().isForbidden());
        assertThat(users.findById(sharedId).orElseThrow().getNickname()).isEqualTo("원래구매자");
    }

    @Test void adminMustNotReadBuyerNotifications() throws Exception {
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test void adminMustNotEnterBuyerPaymentRoute() throws Exception {
        mvc.perform(get("/payments/99999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ROLE_ACCESS_DENIED"));
    }

    @Test void buyerMustNotEnterAdminRoute() throws Exception {
        mvc.perform(get("/admin/refunds").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test void activeAdminCanUseOwnNamespace() throws Exception {
        mvc.perform(get("/admin/refunds").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test void inactiveAdminMustLoseAccessEvenWithUnexpiredToken() throws Exception {
        jdbc.update("UPDATE admins SET status='INACTIVE' WHERE admin_id=?", sharedId);
        mvc.perform(get("/admin/refunds").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test void deletedAdminMustNotAuthenticate() throws Exception {
        jdbc.update("DELETE FROM admins WHERE admin_id=?", sharedId);
        mvc.perform(get("/admin/refunds").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test void buyerCanReadOwnProfile() throws Exception {
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(String.valueOf(sharedId)));
    }

    @Test void anonymousCanStillReadPublicCategories() throws Exception {
        mvc.perform(get("/categories")).andExpect(status().isOk());
    }

    @Test void sellerIsStillABuyerAndCanReadOwnProfile() throws Exception {
        User seller = users.findById(sharedId).orElseThrow();
        seller.promoteToSeller(null);
        users.saveAndFlush(seller);
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isSeller").value(true));
        mvc.perform(get("/sellers/settlements").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test void adminCanStillLogout() throws Exception {
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"not-a-token\"}"))
                .andExpect(status().isOk());
    }

    @Test void unknownRoleCannotFallBackToBuyerIdentity() throws Exception {
        String token = tokens.createAccessToken(sharedId, "UNSUPPORTED", false);
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void anonymousCannotReadPrivateProfile() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test void inactiveBuyerCannotUseUnexpiredToken() throws Exception {
        jdbc.update("UPDATE users SET status='INACTIVE' WHERE user_id=?", sharedId);
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    @Test void adminCannotEnterSellerNamespaceEvenWhenBuyerWithSameIdIsSeller() throws Exception {
        User seller = users.findById(sharedId).orElseThrow();
        seller.promoteToSeller(null);
        users.saveAndFlush(seller);
        mvc.perform(get("/sellers/settlements").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

}
