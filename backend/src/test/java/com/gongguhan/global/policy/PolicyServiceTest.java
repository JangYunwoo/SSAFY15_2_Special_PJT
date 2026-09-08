package com.gongguhan.global.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PolicyServiceTest {

    @Test
    void rateIsReadAsBigDecimalToAvoidBinaryFloatingPointError() {
        PlatformPolicyRepository repository = repositoryWith(PolicyKey.CRYPTO_BUFFER_RATE, "0.003");

        BigDecimal rate = new PolicyService(repository).getRate(PolicyKey.CRYPTO_BUFFER_RATE);

        assertEquals(new BigDecimal("0.003"), rate);
    }

    @Test
    void secondsKeyIsConvertedToDuration() {
        PlatformPolicyRepository repository = repositoryWith(PolicyKey.QUOTE_TTL_SEC, "600");

        Duration ttl = new PolicyService(repository).getSeconds(PolicyKey.QUOTE_TTL_SEC);

        assertEquals(Duration.ofMinutes(10), ttl);
    }

    @Test
    void intKeyIsParsed() {
        PlatformPolicyRepository repository = repositoryWith(PolicyKey.PENALTY_LIMIT, "3");

        assertEquals(3, new PolicyService(repository).getInt(PolicyKey.PENALTY_LIMIT));
    }

    @Test
    void repeatedReadsHitDatabaseOnlyOnce() {
        PlatformPolicyRepository repository = repositoryWith(PolicyKey.PLATFORM_FEE_RATE, "0.05");
        PolicyService service = new PolicyService(repository);

        service.getRate(PolicyKey.PLATFORM_FEE_RATE);
        service.getRate(PolicyKey.PLATFORM_FEE_RATE);
        service.getRate(PolicyKey.PLATFORM_FEE_RATE);

        verify(repository, times(1)).findById(PolicyKey.PLATFORM_FEE_RATE.name());
    }

    @Test
    void evictForcesReloadOnNextRead() {
        PlatformPolicyRepository repository = repositoryWith(PolicyKey.PLATFORM_FEE_RATE, "0.05");
        PolicyService service = new PolicyService(repository);

        service.getRate(PolicyKey.PLATFORM_FEE_RATE);
        service.evict(PolicyKey.PLATFORM_FEE_RATE);
        service.getRate(PolicyKey.PLATFORM_FEE_RATE);

        verify(repository, times(2)).findById(PolicyKey.PLATFORM_FEE_RATE.name());
    }

    /**
     * 기본값으로 조용히 넘어가면 "수수료 0원 정산" 같은 사고가 한참 뒤에 드러난다.
     * 시드 누락은 설정 사고이므로 조회 시점에 바로 터져야 한다.
     */
    @Test
    void missingPolicyRowFailsInsteadOfFallingBackToDefault() {
        PlatformPolicyRepository repository = mock(PlatformPolicyRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PolicyService(repository).getRate(PolicyKey.PLATFORM_FEE_RATE));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
    }

    @Test
    void malformedPolicyValueFails() {
        PlatformPolicyRepository repository = repositoryWith(PolicyKey.PENALTY_LIMIT, "세 번");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PolicyService(repository).getInt(PolicyKey.PENALTY_LIMIT));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
    }

    /** SSAFY 가맹점 ID처럼 발급 전까지 빈 문자열로 시드된 값은 정상 조회되어야 한다. */
    @Test
    void emptyValueIsAValidPolicyValue() {
        PlatformPolicyRepository repository = repositoryWith(PolicyKey.SSAFY_MERCHANT_ID, "");

        assertEquals("", new PolicyService(repository).getString(PolicyKey.SSAFY_MERCHANT_ID));
    }

    @Test
    void updateWritesNewValueAndInvalidatesCache() {
        AtomicReference<String> stored = new AtomicReference<>("0.05");
        AtomicReference<Long> updatedBy = new AtomicReference<>();
        PlatformPolicy policy = statefulPolicy(stored, updatedBy);
        PlatformPolicyRepository repository = mock(PlatformPolicyRepository.class);
        when(repository.findById(PolicyKey.PLATFORM_FEE_RATE.name())).thenReturn(Optional.of(policy));
        PolicyService service = new PolicyService(repository);

        service.getRate(PolicyKey.PLATFORM_FEE_RATE);
        service.update(PolicyKey.PLATFORM_FEE_RATE, "0.03", 1L);

        assertEquals("0.03", stored.get());
        assertEquals(1L, updatedBy.get());
        // 트랜잭션 동기화가 없는 단위 테스트에서는 즉시 무효화된다.
        assertEquals(new BigDecimal("0.03"), service.getRate(PolicyKey.PLATFORM_FEE_RATE));
    }

    @Test
    void updateOnUnknownKeyFails() {
        PlatformPolicyRepository repository = mock(PlatformPolicyRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PolicyService(repository).update(PolicyKey.PLATFORM_FEE_RATE, "0.03", 1L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    private PlatformPolicyRepository repositoryWith(PolicyKey key, String value) {
        // 스텁 객체를 먼저 완성한다. when(...).thenReturn(...) 인자 안에서 다시 스터빙하면
        // Mockito가 바깥 스터빙을 미완료로 보고 UnfinishedStubbingException을 던진다.
        PlatformPolicy policy = policy(value);
        PlatformPolicyRepository repository = mock(PlatformPolicyRepository.class);
        when(repository.findById(key.name())).thenReturn(Optional.of(policy));
        return repository;
    }

    private PlatformPolicy policy(String value) {
        PlatformPolicy policy = mock(PlatformPolicy.class);
        when(policy.getPolicyValue()).thenReturn(value);
        return policy;
    }

    /** changeValue 호출이 실제로 값을 바꾸는 스텁. 캐시 무효화 검증에 필요하다. */
    private PlatformPolicy statefulPolicy(AtomicReference<String> stored,
                                          AtomicReference<Long> updatedBy) {
        PlatformPolicy policy = mock(PlatformPolicy.class);
        when(policy.getPolicyValue()).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            updatedBy.set(invocation.getArgument(1));
            return null;
        }).when(policy).changeValue(anyString(), anyLong());
        return policy;
    }
}
