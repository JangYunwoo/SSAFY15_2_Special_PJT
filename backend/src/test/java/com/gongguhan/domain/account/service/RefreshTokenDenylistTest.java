package com.gongguhan.domain.account.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RefreshTokenDenylistTest {
    @SuppressWarnings("unchecked")
    @Test void consumesUsingSingleConditionalWriteWithExpiryAndNoRawTokenKey() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Duration ttl=Duration.ofMinutes(5);
        when(values.setIfAbsent(anyString(),eq("1"),eq(ttl))).thenReturn(true,false);
        RefreshTokenDenylist store=new RefreshTokenDenylist(redis);
        assertThat(store.tryConsume("QA-token-never-a-real-secret",ttl)).isTrue();
        assertThat(store.tryConsume("QA-token-never-a-real-secret",ttl)).isFalse();
        var keys=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values,times(2)).setIfAbsent(keys.capture(),eq("1"),eq(ttl));
        assertThat(keys.getAllValues()).allSatisfy(key->assertThat(key).startsWith("auth:refresh:denylist:").doesNotContain("QA-token"));
        verify(redis,never()).hasKey(anyString());
    }
    @Test void expiredTokenDoesNotWriteToRedis() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        RefreshTokenDenylist store=new RefreshTokenDenylist(redis);
        assertThat(store.tryConsume("QA",Duration.ZERO)).isFalse();
        assertThat(store.tryConsume("QA",Duration.ofSeconds(-1))).isFalse();
        verifyNoInteractions(redis);
    }
    @SuppressWarnings("unchecked")
    @Test void unavailableOrUnknownRedisResponseMustNotGrantConsumption() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenReturn(null)
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("QA unavailable"));
        RefreshTokenDenylist store=new RefreshTokenDenylist(redis);
        assertThat(store.tryConsume("QA",Duration.ofMinutes(1))).isFalse();
        assertThatThrownBy(()->store.tryConsume("QA",Duration.ofMinutes(1))).isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
    }
}
