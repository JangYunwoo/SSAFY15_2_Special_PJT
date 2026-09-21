package com.gongguhan.domain.account.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.admin.repository.AdminRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.security.JwtProperties;
import com.gongguhan.global.security.JwtTokenProvider;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실제 JWT·서비스·폐기 어댑터를 사용하고 Redis 명령 경계만 메모리 원자 집합으로 대체한다. */
class TokenRotationRegressionTest {
    private JwtTokenProvider tokens;
    private TokenService service;
    private Set<String> revoked;
    private UserRepository users;
    private RefreshTokenDenylist denylist;

    @BeforeEach void setUp() {
        tokens = new JwtTokenProvider(new JwtProperties(
                "isolated-test-signing-key-with-at-least-thirty-two-bytes", 1800, 1209600));
        revoked = java.util.concurrent.ConcurrentHashMap.newKeySet();
        var redis = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> values =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey(anyString())).thenAnswer(call -> revoked.contains(call.getArgument(0)));
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenAnswer(call -> revoked.add(call.getArgument(0)));
        doAnswer(call -> { revoked.add(call.getArgument(0)); return null; })
                .when(values).set(anyString(), eq("1"), any(Duration.class));
        denylist = new RefreshTokenDenylist(redis);
        users = mock(UserRepository.class);
        when(users.findById(1L)).thenReturn(Optional.of(User.create(
                "rotation@example.test", "not-a-login-hash", "QA", "01000000000")));
        service = new TokenService(tokens, denylist, users, mock(AdminRepository.class));
    }

    @Test void rapidSequentialRefreshMustNotRevokeTheNewToken() {
        String current = tokens.createRefreshToken(1L, "USER", false);
        for (int i = 0; i < 100; i++) {
            String previous = current;
            current = service.refresh(previous).refreshToken();
            assertThat(denylist.isRevoked(previous)).isTrue();
            assertThat(denylist.isRevoked(current)).as("새 토큰은 직전 토큰 폐기와 분리되어야 한다").isFalse();
        }
    }

    @Test void oldRefreshTokenMustStillBeRejected() {
        String old = tokens.createRefreshToken(1L, "USER", false);
        service.refresh(old);
        assertThatThrownBy(() -> service.refresh(old)).isInstanceOf(BusinessException.class);
    }

    @Test void logoutMustRevokeCurrentRefreshToken() {
        String current = tokens.createRefreshToken(1L, "USER", false);
        service.logout(current);
        assertThatThrownBy(() -> service.refresh(current)).isInstanceOf(BusinessException.class);
    }

    @Test void concurrentRefreshMustConsumeTheOldTokenOnlyOnce() throws Exception {
        String old=tokens.createRefreshToken(1L,"USER",false);
        var bothValidated=new java.util.concurrent.CyclicBarrier(2);
        when(users.findById(1L)).thenAnswer(call->{
            bothValidated.await(5,java.util.concurrent.TimeUnit.SECONDS);
            return Optional.of(User.create("rotation@example.test","not-a-login-hash","QA","01000000000"));
        });
        java.util.concurrent.Callable<Boolean> refresh=()->{try{service.refresh(old);return true;}catch(BusinessException e){return false;}};
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(refresh);var b=pool.submit(refresh);
            assertThat(java.util.List.of(a.get(10,java.util.concurrent.TimeUnit.SECONDS),b.get(10,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        }
    }

    @Test void logoutAfterInitialCheckMustStillBlockConsumption() {
        String old = tokens.createRefreshToken(1L, "USER", false);
        when(users.findById(1L)).thenAnswer(call -> {
            service.logout(old);
            return Optional.of(User.create("rotation@example.test", "not-a-login-hash", "QA", "01000000000"));
        });
        assertThatThrownBy(() -> service.refresh(old)).isInstanceOf(BusinessException.class);
    }

    @Test void unavailableConsumptionMustNotIssueReplacementTokens() {
        var signer = spy(tokens);
        var unavailable = mock(RefreshTokenDenylist.class);
        when(unavailable.tryConsume(anyString(), any(Duration.class)))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("QA unavailable"));
        var subject = new TokenService(signer, unavailable, users, mock(AdminRepository.class));
        String old = tokens.createRefreshToken(1L, "USER", false);
        assertThatThrownBy(() -> subject.refresh(old))
                .isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
        verify(signer, never()).createAccessToken(anyLong(), anyString(), anyBoolean());
        verify(signer, never()).createRefreshToken(anyLong(), anyString(), anyBoolean());
    }
}
