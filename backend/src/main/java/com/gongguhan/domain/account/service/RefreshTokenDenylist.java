package com.gongguhan.domain.account.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

/**
 * 폐기된 리프레시 토큰 목록(AUTH-04 로그아웃, AUTH-03 재발급 시 로테이션).
 *
 * <p>JWT는 무상태라 서버가 개별 토큰을 무효화하려면 별도 저장이 필요하다. 로그아웃과
 * 재발급(로테이션)에서 쓰인 리프레시 토큰을 Redis에 남은 만료시간(TTL)만큼 담아두고,
 * 재발급 때 대조해 재사용을 막는다. TTL이 지나면 어차피 서명 검증에서 걸리므로 자동 소멸한다.
 */
@Component
public class RefreshTokenDenylist {

    private static final String KEY_PREFIX = "auth:refresh:denylist:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenDenylist(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void revoke(String refreshToken, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(key(refreshToken), "1", ttl);
    }

    public boolean isRevoked(String refreshToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(refreshToken)));
    }

    private String key(String refreshToken) {
        // 토큰 원문을 키로 쓰지 않고 해시한다(길이·노출 최소화).
        return KEY_PREFIX + DigestUtils.md5DigestAsHex(refreshToken.getBytes(StandardCharsets.UTF_8));
    }
}
