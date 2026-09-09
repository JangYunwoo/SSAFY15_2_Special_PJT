package com.gongguhan.domain.groupbuy.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class GroupBuySlotGateTest {

    private StringRedisTemplate redisTemplate;
    private RedisScript<Long> reserveScript;
    private RedisScript<Long> releaseScript;
    private GroupBuySlotGate gate;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        reserveScript = mock(RedisScript.class);
        releaseScript = mock(RedisScript.class);
        gate = new GroupBuySlotGate(redisTemplate, reserveScript, releaseScript);
    }

    @Test
    void tryReserveReturnsTrueWhenScriptGrants() {
        when(redisTemplate.execute(eq(reserveScript), anyList(), anyString(), anyString())).thenReturn(1L);

        assertTrue(gate.tryReserve(1L, 50, 0));
    }

    @Test
    void tryReserveReturnsFalseWhenScriptRejects() {
        when(redisTemplate.execute(eq(reserveScript), anyList(), anyString(), anyString())).thenReturn(0L);

        assertFalse(gate.tryReserve(1L, 50, 50));
    }

    @Test
    void tryReservePassesTargetAndDbTruthAsScriptArgs() {
        when(redisTemplate.execute(eq(reserveScript), eq(List.of("slot-gate:group-buy:1")), eq("50"), eq("12")))
                .thenReturn(1L);

        assertTrue(gate.tryReserve(1L, 50, 12));
    }

    @Test
    void releaseInvokesReleaseScriptWithGateKey() {
        gate.release(1L);

        verify(redisTemplate).execute(releaseScript, List.of("slot-gate:group-buy:1"));
    }
}
