package com.gongguhan.domain.account.service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일 중복확인 때 확보한 SSAFY 회원키(userKey)를 짧게 보관했다가 가입 시 재사용한다(C안).
 *
 * <p>중복확인이 이미 {@code createMember}로 회원키를 확보했는데 가입에서 또 부르면 SSAFY를
 * 두 번 때리게 된다. 확인 시 {@link #reserve}로 담아두고 가입 시 {@link #consume}으로 꺼내
 * 쓰면 외부 호출 낭비가 사라진다. 정확성의 필수 요소가 아니라 <b>최적화</b>이므로, 캐시가
 * 없거나(만료) Redis 장애면 가입 경로가 그냥 다시 {@code resolveSsafyMember}를 부른다.
 *
 * <p>이메일 비교는 {@code users.email}의 utf8mb4_unicode_ci(대소문자 무시)와 맞추기 위해
 * 소문자로 정규화해 키로 쓴다.
 */
@Component
public class SsafyMemberReservationStore {

    private static final Logger log = LoggerFactory.getLogger(SsafyMemberReservationStore.class);
    private static final String KEY_PREFIX = "ssafy:member:reserve:";
    /** 확인 후 가입까지의 여유. 프로필 입력 시간을 감안해 넉넉히 잡되 무한정 두지 않는다. */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public SsafyMemberReservationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 확인 성공 시 email→userKey를 TTL과 함께 담는다. 실패해도 가입은 재확인으로 진행되므로 삼킨다. */
    public void reserve(String email, String userKey) {
        if (userKey == null || userKey.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(email), userKey, TTL);
        } catch (RuntimeException e) {
            log.warn("이메일 예약 캐시 저장 실패 email={} - 가입 시 재확인된다", email, e);
        }
    }

    /** 가입 시 예약된 userKey를 꺼내며 즉시 삭제한다(원자적 GETDEL). 없으면 empty. */
    public Optional<String> consume(String email) {
        try {
            String userKey = redisTemplate.opsForValue().getAndDelete(key(email));
            return Optional.ofNullable(userKey).filter(value -> !value.isBlank());
        } catch (RuntimeException e) {
            log.warn("이메일 예약 캐시 조회 실패 email={} - 가입 시 재확인된다", email, e);
            return Optional.empty();
        }
    }

    private static String key(String email) {
        return KEY_PREFIX + email.trim().toLowerCase(Locale.ROOT);
    }
}
