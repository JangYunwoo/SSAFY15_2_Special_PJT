package com.gongguhan.domain.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongguhan.domain.groupbuy.dto.GroupBuyJudgementResult;
import com.gongguhan.domain.groupbuy.event.GroupBuyFailedEvent;
import com.gongguhan.domain.groupbuy.event.GroupBuyFulfilledEvent;
import com.gongguhan.domain.groupbuy.service.GroupBuyRecruitmentService;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class GroupBuyRecruitmentIntegrationTest {

    private static final long FULFILLED_ID = 920_001L;
    private static final long FAILED_ID = 920_002L;
    private static final long FUTURE_ID = 920_003L;
    private static final long IMMEDIATE_ID = 920_004L;

    @Autowired
    private GroupBuyRecruitmentService groupBuyRecruitmentService;

    @Autowired
    private GroupBuySlotService groupBuySlotService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createPolicyTable();
        LocalDateTime utcNow = LocalDateTime.now(Clock.systemUTC());
        insertGroupBuy(FULFILLED_ID, 2, 2, 1, utcNow.plusDays(1));
        insertGroupBuy(FAILED_ID, 2, 1, 1, utcNow.minusMinutes(1));
        insertGroupBuy(FUTURE_ID, 2, 1, 0, utcNow.plusDays(1));
    }

    private void createPolicyTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS platform_policies (
                    policy_key VARCHAR(100) PRIMARY KEY,
                    policy_value VARCHAR(255) NOT NULL
                )
                """);
        jdbcTemplate.update("""
                MERGE INTO platform_policies (policy_key, policy_value) KEY(policy_key)
                VALUES ('SHIPPING_DEADLINE_DAYS', '3')
                """);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM group_buys WHERE group_buy_id IN (?, ?, ?, ?)",
                FULFILLED_ID,
                FAILED_ID,
                FUTURE_ID,
                IMMEDIATE_ID);
    }

    @Test
    void judgesPaidCountOnlyAndPublishesDomainEvents() {
        GroupBuyJudgementResult result = groupBuyRecruitmentService.judgeCandidates();

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.fulfilledCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertGroupBuy(FULFILLED_ID, "FULFILLED", 2, 0);
        assertGroupBuy(FAILED_ID, "FAILED", 1, 0);
        assertGroupBuy(FUTURE_ID, "RECRUITING", 1, 0);

        LocalDateTime fulfilledAt = value(FULFILLED_ID, "fulfilled_at", LocalDateTime.class);
        LocalDateTime deadlineAt = value(FULFILLED_ID, "shipping_deadline_at", LocalDateTime.class);
        assertThat(fulfilledAt).isNotNull();
        assertThat(deadlineAt).isAfter(fulfilledAt);
        assertThat(deadlineAt.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(applicationEvents.stream(GroupBuyFulfilledEvent.class)).hasSize(1);
        assertThat(applicationEvents.stream(GroupBuyFailedEvent.class)).hasSize(1);
    }

    @Test
    void rerunIsIdempotent() {
        groupBuyRecruitmentService.judgeCandidates();

        GroupBuyJudgementResult rerun = groupBuyRecruitmentService.judgeCandidates();

        assertThat(rerun.processedCount()).isZero();
        assertThat(rerun.fulfilledCount()).isZero();
        assertThat(rerun.failedCount()).isZero();
    }

    @Test
    void confirmingLastReservedSlotImmediatelyFulfillsRecruitment() {
        LocalDateTime utcNow = LocalDateTime.now(Clock.systemUTC());
        insertGroupBuy(IMMEDIATE_ID, 2, 1, 1, utcNow.plusDays(1));

        groupBuySlotService.confirm(IMMEDIATE_ID);

        assertGroupBuy(IMMEDIATE_ID, "FULFILLED", 2, 0);
        assertThat(value(IMMEDIATE_ID, "fulfilled_at", LocalDateTime.class)).isNotNull();
        assertThat(value(IMMEDIATE_ID, "shipping_deadline_at", LocalDateTime.class)).isNotNull();
        assertThat(applicationEvents.stream(GroupBuyFulfilledEvent.class)
                .filter(event -> event.groupBuyId().equals(IMMEDIATE_ID)))
                .hasSize(1);
    }

    @Test
    void failureJudgementDoesNotDependOnShippingDeadlinePolicy() {
        jdbcTemplate.update(
                "DELETE FROM group_buys WHERE group_buy_id IN (?, ?)",
                FULFILLED_ID,
                FUTURE_ID);
        jdbcTemplate.update("""
                UPDATE platform_policies
                   SET policy_value = 'invalid'
                 WHERE policy_key = 'SHIPPING_DEADLINE_DAYS'
                """);

        GroupBuyJudgementResult result = groupBuyRecruitmentService.judgeCandidates();

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertGroupBuy(FAILED_ID, "FAILED", 1, 0);
    }

    @Test
    void adminCanRunRecruitmentJobManually() throws Exception {
        mockMvc.perform(post("/admin/jobs/group-buy-recruitment/run")
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processedCount").value(2))
                .andExpect(jsonPath("$.data.fulfilledCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1));
    }

    @Test
    void nonAdminCannotRunRecruitmentJobManually() throws Exception {
        mockMvc.perform(post("/admin/jobs/group-buy-recruitment/run")
                        .with(authentication(user())))
                .andExpect(status().isForbidden());
    }

    private void insertGroupBuy(
            long id, int targetParticipants, int currentParticipants, int reservedSlots, LocalDateTime recruitEndAt) {
        jdbcTemplate.update("""
                INSERT INTO group_buys (
                    group_buy_id, product_id, seller_id, target_participants,
                    unit_share_amount, shipping_fee, current_participants, reserved_slots,
                    recruit_end_at, status, version, created_at, updated_at
                ) VALUES (?, 1, 1, ?, 50000, 4000, ?, ?, ?, 'RECRUITING', 0, NOW(), NOW())
                """,
                id,
                targetParticipants,
                currentParticipants,
                reservedSlots,
                recruitEndAt);
    }

    private void assertGroupBuy(long id, String status, int currentParticipants, int reservedSlots) {
        assertThat(value(id, "status", String.class)).isEqualTo(status);
        assertThat(value(id, "current_participants", Integer.class)).isEqualTo(currentParticipants);
        assertThat(value(id, "reserved_slots", Integer.class)).isEqualTo(reservedSlots);
    }

    private <T> T value(long id, String column, Class<T> type) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM group_buys WHERE group_buy_id = ?",
                type,
                id);
    }

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken user() {
        return new UsernamePasswordAuthenticationToken(
                2L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
