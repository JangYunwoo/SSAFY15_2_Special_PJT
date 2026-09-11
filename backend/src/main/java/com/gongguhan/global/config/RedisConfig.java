package com.gongguhan.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 공동구매 슬롯 예약 Redis fast-fail 게이트({@link com.gongguhan.domain.groupbuy.service.GroupBuySlotGate})가
 * 사용하는 원자적 Lua 스크립트를 빈으로 등록한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<Long> groupBuySlotGateScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/group_buy_slot_gate.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<Long> groupBuySlotReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/group_buy_slot_release.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
