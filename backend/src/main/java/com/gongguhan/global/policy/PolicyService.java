package com.gongguhan.global.policy;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@code platform_policies} 조회와 타입 변환.
 *
 * <p>수수료율·Buffer율·슬롯 TTL·각종 기한처럼 <b>금액과 마감 시각을 결정하는 값</b>을
 * 코드가 아니라 DB에서 읽는다. 관리자(ADM)가 재배포 없이 바꿀 수 있어야 하기 때문이다.
 *
 * <p><b>코드에 기본값을 두지 않는다.</b> 정책 행이 없으면 예외를 던진다. 기본값을 두면
 * 시드 누락이나 오타가 조용히 넘어가고, 그 결과가 "수수료가 0원으로 정산됨" 같은 형태로
 * 한참 뒤에 드러난다. 값은 V1__init_schema.sql이 전부 시드하므로, 조회 실패는
 * 설정 사고이지 정상 경로가 아니다.
 *
 * <p><b>캐시.</b> 정책값은 거의 바뀌지 않는데 결제·정산 경로에서 매번 읽힌다. 프로세스
 * 로컬 맵에 담고 {@link #update}나 {@link #evict}로만 비운다. 현재 단일 인스턴스라
 * 이것으로 충분하고, 인스턴스를 늘리면 갱신 전파(Redis pub/sub 등)가 별도로 필요하다.
 */
@Service
@Transactional(readOnly = true)
public class PolicyService {

    private final PlatformPolicyRepository platformPolicyRepository;
    private final Map<PolicyKey, String> cache = new ConcurrentHashMap<>();

    public PolicyService(PlatformPolicyRepository platformPolicyRepository) {
        this.platformPolicyRepository = platformPolicyRepository;
    }

    public String getString(PolicyKey key) {
        // computeIfAbsent를 쓰지 않는다. 매핑 함수 안에서 DB를 치면 그동안 해당 버킷이
        // 잠겨 다른 키 조회까지 막힌다. 중복 조회가 몇 번 일어나는 편이 낫다.
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        String value = platformPolicyRepository.findById(key.name())
                .map(PlatformPolicy::getPolicyValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "정책값이 설정되어 있지 않습니다: %s".formatted(key.name())));

        cache.put(key, value);
        return value;
    }

    public int getInt(PolicyKey key) {
        return parse(key, Integer::parseInt);
    }

    public long getLong(PolicyKey key) {
        return parse(key, Long::parseLong);
    }

    /** 수수료율·Buffer율처럼 비율로 쓰는 값. 부동소수점 오차를 피하려 BigDecimal로 읽는다. */
    public BigDecimal getRate(PolicyKey key) {
        return parse(key, BigDecimal::new);
    }

    /** {@code *_SEC} 키 전용. 초 단위 정수를 Duration으로 바꾼다. */
    public Duration getSeconds(PolicyKey key) {
        return Duration.ofSeconds(getLong(key));
    }

    /** {@code *_MS} 키 전용. */
    public Duration getMillis(PolicyKey key) {
        return Duration.ofMillis(getLong(key));
    }

    /**
     * 관리자 정책 변경 (FR-ADM-13).
     *
     * <p>캐시 무효화는 <b>커밋 이후</b>로 미룬다. 트랜잭션 안에서 비우면 아직 flush 전인
     * 옛 값을 다른 요청이 읽어 캐시를 다시 채우고, 커밋이 끝나도 캐시만 낡은 채로 남는다.
     * 롤백된 경우에는 캐시를 건드릴 이유가 없으므로 afterCommit이 정확한 지점이다.
     *
     * @param updatedBy 변경한 관리자 ID
     */
    @Transactional
    public void update(PolicyKey key, String value, Long updatedBy) {
        PlatformPolicy policy = platformPolicyRepository.findById(key.name())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "정책값을 찾을 수 없습니다: %s".formatted(key.name())));

        policy.changeValue(value, updatedBy);
        evictAfterCommit(key);
    }

    public void evict(PolicyKey key) {
        cache.remove(key);
    }

    public void evictAll() {
        cache.clear();
    }

    /** 트랜잭션이 없으면(테스트·초기화 경로) 즉시 비운다. */
    private void evictAfterCommit(PolicyKey key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(key);
            }
        });
    }

    private <T> T parse(PolicyKey key, Function<String, T> parser) {
        String raw = getString(key);
        try {
            return parser.apply(raw);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "정책값 형식이 올바르지 않습니다: %s = %s".formatted(key.name(), raw));
        }
    }
}
