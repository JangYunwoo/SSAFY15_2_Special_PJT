package com.gongguhan.domain.groupbuy.service;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 공동구매 슬롯 예약을 위한 Redis 기반 fast-fail 게이트.
 *
 * <p>DB 트랜잭션의 비관적 락({@code SELECT ... FOR UPDATE})에 도달하기 전에 정원 초과
 * 요청을 Redis 원자적 카운터로 먼저 거부해, 대부분 거부될 고경합 상황에서 MySQL 행
 * 잠금 대기열에 쌓이는 요청 수를 줄인다. DB는 여전히 최종 진실 소스이며, 이 게이트는
 * 순수 성능 최적화용 사전 체크일 뿐이다 — 게이트가 어떤 이유로든 실제보다 더
 * 관대해지더라도(예: release만 되고 DB 반영이 없는 경우) 정합성은 DB의 비관적 락과
 * {@code ck_group_buys_slots} CHECK 제약이 최종적으로 보장한다.
 *
 * <p>호출자가 매번 {@code targetParticipants}/{@code dbTruth}(reserved+current)를
 * 넘겨준다 — 락 없는 단순 조회이므로 InnoDB의 다른 트랜잭션 행 잠금에 걸리지 않는다.
 * Redis 키가 이미 존재하면 {@code dbTruth}는 Lua의 {@code SETNX}가 조용히 무시하므로
 * 매번 넘겨도 비용이 없다.
 */
@Component
public class GroupBuySlotGate {

    private static final String KEY_PREFIX = "slot-gate:group-buy:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> reserveScript;
    private final RedisScript<Long> releaseScript;

    public GroupBuySlotGate(StringRedisTemplate redisTemplate,
                             RedisScript<Long> groupBuySlotGateScript,
                             RedisScript<Long> groupBuySlotReleaseScript) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = groupBuySlotGateScript;
        this.releaseScript = groupBuySlotReleaseScript;
    }

    /**
     * @param dbTruth {@code reserved_slots + current_participants} — 락 없는 조회로 얻은 값.
     *                Redis 키가 이미 존재하면 Lua 쪽에서 무시된다.
     * @return 슬롯이 잠정 승인되면 true, 정원 초과로 거부되면 false
     */
    public boolean tryReserve(Long groupBuyId, int targetParticipants, int dbTruth) {
        Long result = redisTemplate.execute(
                reserveScript,
                List.of(key(groupBuyId)),
                String.valueOf(targetParticipants),
                String.valueOf(dbTruth));
        return result != null && result == 1L;
    }

    public void release(Long groupBuyId) {
        redisTemplate.execute(releaseScript, List.of(key(groupBuyId)));
    }

    private String key(Long groupBuyId) {
        return KEY_PREFIX + groupBuyId;
    }
}
