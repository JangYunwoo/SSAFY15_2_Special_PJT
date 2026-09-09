package com.gongguhan.domain.escrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongguhan.domain.escrow.dto.EscrowAdminSummaryResponse;
import com.gongguhan.domain.escrow.service.EscrowQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ESC-03 관리자 집계 통합 검증.
 *
 * <p><b>이 테스트가 필요한 이유.</b> 단위 테스트는 리포지토리를 mock 해서 JPQL 이
 * 한 번도 실행되지 않습니다. {@code aggregateSettled()} 는 연관관계 없이 ID 로 조인하고
 * {@code count} 같은 SQL 예약어를 별칭으로 쓰는 인터페이스 프로젝션이라, 파싱은 되지만
 * 실행에서 깨질 수 있습니다. 실제로 쿼리를 돌려 확인합니다.
 *
 * <p>관리자 인증({@code /auth/admins/login})이 아직 구현되지 않아 실제 토큰을 발급받을
 * 수 없습니다. {@code MockMvc} 의 {@code authentication()} 으로 ROLE_ADMIN 을 주입해
 * 권한 규칙과 응답 형태를 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EscrowAdminSummaryIntegrationTest {

    private static final long AMOUNT = 54_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EscrowQueryService escrowQueryService;

    /**
     * HELD 2건, RELEASED 3건(그중 1건은 참여 건이 SETTLED), REFUNDED 1건.
     *
     * <p>기대값:
     * <pre>
     *   보관     2건 108,000원
     *   정산대기 2건 108,000원   (RELEASED 3건 − 정산완료 1건)
     *   정산완료 1건  54,000원
     *   환불     1건  54,000원
     *   합계     6건 324,000원
     * </pre>
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM escrows");
        jdbcTemplate.update("DELETE FROM participations");

        insertParticipation(1L, "PAID");
        insertParticipation(2L, "PAID");
        insertParticipation(3L, "CONFIRMED");
        insertParticipation(4L, "CONFIRMED");
        insertParticipation(5L, "SETTLED");
        insertParticipation(6L, "REFUNDED");

        insertEscrow(1L, 1L, "HELD");
        insertEscrow(2L, 2L, "HELD");
        insertEscrow(3L, 3L, "RELEASED");
        insertEscrow(4L, 4L, "RELEASED");
        insertEscrow(5L, 5L, "RELEASED");
        insertEscrow(6L, 6L, "REFUNDED");
    }

    /**
     * {@code settled} 컬럼은 넣지 않습니다.
     *
     * <p>테스트 프로파일은 H2 + {@code ddl-auto: create-drop} 이라 테이블이 <b>엔티티에서</b>
     * 생성됩니다. {@code Participation} 엔티티에 {@code settled} 매핑이 없어 H2 테이블에도
     * 그 컬럼이 없습니다(실제 MySQL 에는 있습니다).
     *
     * <p>집계 쿼리가 {@code settled} 가 아니라 {@code status} 를 보도록 만든 이유가
     * 여기서 드러납니다 — 매핑되지 않은 컬럼은 JPQL 로 접근할 수 없습니다.
     */
    private void insertParticipation(Long participationId, String status) {
        jdbcTemplate.update("""
                INSERT INTO participations
                  (participation_id, group_buy_id, user_id, share_amount, shipping_fee,
                   total_amount, recipient_name, recipient_phone, zipcode, address1,
                   status, joined_at)
                VALUES (?, 1, 1, 50000, 4000, ?, 'T', '010', '06236', 'A', ?, CURRENT_TIMESTAMP)
                """, participationId, AMOUNT, status);
    }

    private void insertEscrow(Long escrowId, Long participationId, String status) {
        jdbcTemplate.update("""
                INSERT INTO escrows
                  (escrow_id, participation_id, group_buy_id, amount, status, held_at)
                VALUES (?, ?, 1, ?, ?, CURRENT_TIMESTAMP)
                """, escrowId, participationId, AMOUNT, status);
    }

    // ------------------------------------------------------------------
    // 쿼리 실행 검증
    // ------------------------------------------------------------------

    @Test
    @DisplayName("집계 JPQL 이 실제로 실행되고 정산 대기 금액이 정확하다")
    void aggregatesCorrectlyAgainstRealQueries() {
        EscrowAdminSummaryResponse summary = escrowQueryService.getAdminSummary();

        assertThat(summary.heldCount()).isEqualTo(2);
        assertThat(summary.heldAmount()).isEqualTo(108_000L);

        // RELEASED 3건 중 참여 건이 SETTLED 인 1건을 뺀 값
        assertThat(summary.awaitingSettlementCount()).isEqualTo(2);
        assertThat(summary.awaitingSettlementAmount()).isEqualTo(108_000L);

        assertThat(summary.settledCount()).isEqualTo(1);
        assertThat(summary.settledAmount()).isEqualTo(54_000L);

        assertThat(summary.refundedCount()).isEqualTo(1);
        assertThat(summary.refundedAmount()).isEqualTo(54_000L);

        assertThat(summary.totalCount()).isEqualTo(6);
        assertThat(summary.totalAmount()).isEqualTo(324_000L);
        assertThat(summary.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("데이터가 없어도 쿼리가 깨지지 않고 0 을 돌려준다")
    void handlesEmptyTables() {
        jdbcTemplate.update("DELETE FROM escrows");
        jdbcTemplate.update("DELETE FROM participations");

        EscrowAdminSummaryResponse summary = escrowQueryService.getAdminSummary();

        assertThat(summary.totalCount()).isZero();
        assertThat(summary.totalAmount()).isZero();
        assertThat(summary.awaitingSettlementAmount()).isZero();
    }

    // ------------------------------------------------------------------
    // 권한
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ADMIN 권한으로 200 과 집계 응답을 받는다")
    void returnsSummaryForAdmin() throws Exception {
        mockMvc.perform(get("/admin/escrows/summary").with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.heldAmount").value(108_000))
                .andExpect(jsonPath("$.data.awaitingSettlementAmount").value(108_000))
                .andExpect(jsonPath("$.data.settledAmount").value(54_000))
                .andExpect(jsonPath("$.data.currency").value("KRW"));
    }

    @Test
    @DisplayName("USER 권한으로는 403 - 관리자 전용")
    void rejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/admin/escrows/summary").with(userAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이는 401")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/admin/escrows/summary"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor userAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
