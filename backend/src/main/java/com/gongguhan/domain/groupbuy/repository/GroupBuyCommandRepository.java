package com.gongguhan.domain.groupbuy.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class GroupBuyCommandRepository {

    private static final String INSERT_SQL = """
            INSERT INTO group_buys (
                product_id, seller_id, product_price, target_participants,
                unit_share_amount, shipping_fee, rounding_diff, platform_fee_rate,
                expected_platform_fee, expected_settlement_amount,
                recruit_start_at, recruit_end_at,
                current_participants, reserved_slots, status, version,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 'RECRUITING', 0,
                      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;

    private final JdbcTemplate jdbcTemplate;

    public GroupBuyCommandRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StoredGroupBuy create(GroupBuyCreateCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, command.productId());
            statement.setLong(2, command.sellerId());
            statement.setLong(3, command.productPrice());
            statement.setInt(4, command.targetParticipants());
            statement.setLong(5, command.unitShareAmount());
            statement.setLong(6, command.shippingFee());
            statement.setLong(7, command.roundingDiff());
            statement.setBigDecimal(8, command.platformFeeRate());
            statement.setLong(9, command.expectedPlatformFee());
            statement.setLong(10, command.expectedSettlementAmount());
            statement.setObject(11, command.recruitStartAt());
            statement.setObject(12, command.recruitEndAt());
            return statement;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("공동구매 ID를 생성하지 못했습니다.");
        }

        return jdbcTemplate.queryForObject(
                "SELECT created_at, updated_at FROM group_buys WHERE group_buy_id = ?",
                (resultSet, rowNumber) -> new StoredGroupBuy(
                        generatedKey.longValue(),
                        resultSet.getObject("created_at", LocalDateTime.class),
                        resultSet.getObject("updated_at", LocalDateTime.class)),
                generatedKey.longValue());
    }

    public record StoredGroupBuy(Long groupBuyId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
