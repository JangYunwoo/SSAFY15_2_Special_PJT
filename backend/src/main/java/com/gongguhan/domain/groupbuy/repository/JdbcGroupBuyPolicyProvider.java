package com.gongguhan.domain.groupbuy.repository;

import com.gongguhan.domain.groupbuy.service.GroupBuyPolicyProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGroupBuyPolicyProvider implements GroupBuyPolicyProvider {

    private static final String SHIPPING_DEADLINE_POLICY_KEY = "SHIPPING_DEADLINE_DAYS";

    private final JdbcTemplate jdbcTemplate;

    public JdbcGroupBuyPolicyProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int shippingDeadlineBusinessDays() {
        String value = jdbcTemplate.queryForObject(
                "SELECT policy_value FROM platform_policies WHERE policy_key = ?",
                String.class,
                SHIPPING_DEADLINE_POLICY_KEY);
        int businessDays;
        try {
            businessDays = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("배송기한 정책값이 올바른 정수가 아닙니다.", exception);
        }
        if (businessDays < 0) {
            throw new IllegalStateException("배송기한 정책값은 0 이상이어야 합니다.");
        }
        return businessDays;
    }
}
