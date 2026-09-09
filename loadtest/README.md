# 결제 슬롯 예약 부하테스트 — Redis Fast-Fail 게이트 Before/After

## 배경

`PaymentService.prepare()`는 `group_buys` 행에 `SELECT ... FOR UPDATE`(비관적 락)를
걸고 그 안에서 참여/슬롯 중복 체크, 주소 소유권 체크, `Payment` 행 저장까지 전부
수행한다. 인기 있는 공동구매(슬롯은 적은데 동시 요청이 몰리는 상황)에서는 이
행 잠금이 모든 요청 — 대부분 결국 `GROUP_BUY_FULL`로 거부될 요청까지 — 을
직렬화시킨다.

이 부하테스트는 이 병목을 실측하고, Redis 원자적 카운터를 DB 트랜잭션 앞단에
"빠른 거부(fast-fail)" 게이트로 추가했을 때 얼마나 개선되는지 before/after로
비교한다. Redis는 게이트 역할만 하고, 기존 DB 비관적 락 트랜잭션은 그대로
유지한다(Option A — DB가 여전히 최종 진실 소스).

## 재현 방법

```bash
# 1) MySQL, Redis 기동 (이 부하테스트는 포트 충돌 회피를 위해 3307에 별도 MySQL
#    컨테이너를 썼다 - 로컬에 3306을 이미 쓰는 프로세스가 있다면 docker-compose.yml
#    대신 별도 컨테이너를 띄우거나 포트를 조정한다)
docker compose up -d   # 또는 3306이 막혀 있으면 redis만: docker compose up -d redis

# 2) 시드 데이터 투입 (before/after 각 실행 전 매번 재실행 — 카운터 리셋 포함, 재실행 안전)
docker exec -i <mysql-container> mysql -ugongguhan -pgongguhan1234! gongguhan < loadtest/seed.sql
docker exec <redis-container> redis-cli DEL "slot-gate:group-buy:9000001"

# 3) 앱 기동 (JWT_SECRET을 k6 스크립트와 동일한 값으로 명시 지정)
#    HikariCP/Tomcat 설정을 늘려서 커넥션 풀·accept 큐 대기가 아니라 실제 DB
#    락 경합을 측정한다.
cd backend
JWT_SECRET=loadtest-shared-secret-please-change \
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=80 \
SERVER_TOMCAT_THREADS_MAX=400 SERVER_TOMCAT_ACCEPT_COUNT=500 SERVER_TOMCAT_MAX_CONNECTIONS=1000 \
./gradlew bootRun

# 4) k6 실행 (다른 터미널에서, before/after 동일한 커맨드로 재실행)
k6 run -e JWT_SECRET=loadtest-shared-secret-please-change -e RUN_LABEL=before \
  --summary-export=loadtest/results/before-trialN.json \
  loadtest/k6/prepare-slot.js
```

`after` 측정 전에는 Redis 게이트가 적용된 커밋으로 앱을 재기동해야 한다.
`before`(게이트 이전 커밋)와 `after`(게이트 적용 커밋)를 동시에 띄우지 않는다 —
두 앱이 같은 MySQL을 공유하면 Hikari 풀 합계가 MySQL `max_connections`(기본
151)를 넘겨 `Too many connections` 오류로 측정치가 오염된다. **한 번에 하나만
띄운다.**

## 시나리오

- 공동구매 1건, `target_participants=50`
- 서로 다른 900명의 시드 사용자, 각자 정확히 1회씩만 요청 (재시도/중복 없음 →
  `DUPLICATE_PARTICIPATION`이 `GROUP_BUY_FULL`과 섞여 집계되는 것을 방지)
- k6 `shared-iterations` executor로 짧은 시간에 몰아서 요청 (지속 부하가 아니라
  순간 폭주 시나리오 — 실제 한정 수량 이벤트와 동일한 모양)
- 기대 결과: 성공(201) 50건, 거부(409 `GROUP_BUY_FULL`) 850건

## 측정 방법론 — 왜 트라이얼을 여러 번 돌렸나

처음에 before/after 각 1회씩만 측정했을 때 결과가 실행마다 크게 흔들렸다
(예: 같은 after 코드로 5.9초 → 1.3초 → 5.1초까지 널뛰었다). 원인을 둘로
좁혔다:

1. **앱을 두 개(before/after) 동시에 띄운 상태로 측정** — 위에서 설명한
   MySQL 커넥션 고갈이 측정치를 오염시켰다. → 반드시 한 번에 하나만 기동.
2. **앱 재기동 직후 첫 실행은 항상 비정상적으로 느리다** (JIT 워밍업, 커넥션
   풀 콜드 스타트, 쿼리 플랜 캐시 없음 등) — before/after 양쪽 코드에서
   동일하게 재현됐다.

그래서 각 조건(before/after)마다 **앱을 한 번만 기동한 채로 연속 트라이얼을
여러 번 돌리고, 재기동 직후 첫 트라이얼만 콜드 스타트로 간주해 제외**했다.
원본 raw 데이터는 전부 `results/before-trial{1..5}.json`,
`results/after-trial{1..6}.json`로 남겨뒀다(`before-trial1`, `after-trial4`가
콜드 스타트 케이스 — 삭제하지 않고 방법론의 증거로 남김).

## 결과 (콜드 스타트 제외, Before n=4 / After n=5 평균)

| 지표 | Before (DB 락만) | After (Redis 게이트) | 변화 |
| --- | --- | --- | --- |
| 처리량 (req/s) | 209.5 | 650.6 | **3.11배** |
| reject_latency 평균 | 1254 ms | 265 ms | **4.74배 감소** |
| reject_latency p95 | 1816 ms | 438 ms | **4.15배 감소** |
| success_latency 평균 | 598 ms | 742 ms | **24% 증가** |
| success_latency p95 | 1058 ms | 1287 ms | **22% 증가** |
| unexpected_errors | 0 | 0 | 동일 |

### 해석

**거부 경로는 설계 의도대로 극적으로 개선됐다.** 정원 초과 요청(900건 중
850건, 94%)이 더 이상 MySQL 비관적 락 대기열에 들어가지 않고 Redis 원자적
카운터에서 즉시 걸러지기 때문이다. 이 시나리오(소수 슬롯에 다수가 몰리는
"선착순" 상황)에서는 트래픽의 대부분이 거부 경로이므로, 전체 처리량도 약
3배 개선됐다.

**성공 경로는 오히려 소폭 느려졌다.** 두 가지 이유로 추정한다:
1. 성공 요청도 게이트 사전 체크(락 없는 조회 + Redis 왕복)를 한 번 더 거치므로
   요청당 고정 오버헤드가 늘었다.
2. Before에서는 거부 트래픽이 락 대기열 때문에 자연히 시간에 걸쳐 분산됐지만,
   After에서는 거부 트래픽 850건이 락을 거치지 않고 거의 동시에 몰려서 짧은
   시간 안에 MySQL에 훨씬 집중된 쿼리 폭주(요청당 조회 2회 × 850건)를
   만든다. 이 폭주가 동시에 진행 중인 50건의 성공 트랜잭션과 MySQL 자체의
   처리 용량(스레드, 네트워크 I/O)을 두고 경합했을 가능성이 있다.

**50건의 성공 요청 자체는 여전히 하나의 행 락으로 완전히 직렬화된다** — 이건
이번 게이트가 애초에 해결하려던 문제가 아니다(Option A 범위: DB는 그대로
최종 진실 소스). 성공 경로 자체의 지연시간을 줄이려면 더 큰 변경(비관적 락 →
낙관적 락 + 재시도, `group_buys.version` 활용)이 필요하며, 이는 의도적으로
범위 밖으로 남겨뒀다 — 트레이드오프와 향후 과제는 바탕화면 보고서 참고.

## 알려진 한계

- 로컬 단일 인스턴스 기준 측정. `GroupBuySlotGate`의 로컬 캐시(대상 인원수)는
  다중 인스턴스 환경에서도 정합성엔 문제없지만(Lua 스크립트의 원자성이
  정합성을 담당), 이 벤치마크로는 다중 인스턴스 시나리오를 검증하지 않았다.
- MySQL/앱을 같은 로컬 머신의 Docker Desktop(WSL2) 위에서 돌렸다. 운영
  환경(별도 DB 서버, 실제 네트워크 지연)에서는 절대값이 다를 수 있다 —
  다만 "거부 경로가 락 대기열을 우회한다"는 상대적 개선 방향은 인프라와
  무관하게 유효하다.
- 트라이얼 수(before n=4, after n=5)는 통계적으로 크지 않다. 방향성과
  크기는 명확하지만, 신뢰구간 계산 수준의 엄밀함은 아니다.
