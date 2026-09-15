package com.gongguhan.domain.account.service;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일 확인·예약 엔드포인트의 호출 빈도 제한(C안).
 *
 * <p>확인이 SSAFY {@code createMember}(쓰기)를 유발하므로, 비인증 엔드포인트가 남용되면
 * 이메일 열거와 외부 API 레이트리밋 소진으로 이어진다. 클라이언트별 고정 윈도 카운터로
 * 방어한다. Redis 장애 시에는 정상 사용자를 막지 않도록 <b>fail-open</b>한다(가입 시점 방어가
 * 최종 관문이므로 확인 단계에서 과하게 막지 않는다).
 */
@Component
public class EmailCheckRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(EmailCheckRateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:email-check:";
    private static final int LIMIT = 20;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    public EmailCheckRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 한도를 넘으면 {@link ErrorCode#TOO_MANY_REQUESTS}를 던진다. */
    public void check(String clientKey) {
        try {
            String key = KEY_PREFIX + clientKey;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
            if (count != null && count > LIMIT) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("이메일 확인 레이트리밋 사용 불가 - 요청 허용(fail-open) clientKey={}", clientKey, e);
        }
    }
}
