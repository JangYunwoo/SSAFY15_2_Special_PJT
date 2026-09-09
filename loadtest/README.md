# 결제 슬롯 예약 부하테스트 — Redis Fast-Fail 게이트 Before/After

## 배경

`PaymentService.prepare()`는 `group_buys` 행에 `SELECT ... FOR UPDATE`(비관적 락)를
걸고 그 안에서 참여/슬롯 중복 체크, 주소 소유권 체크, `Payment` 행 저장까지 전부
수행한다. 인기 있는 공동구매(슬롯은 적은데 동시 요청이 몰리는 상황)에서는 이
행 잠금이 모든 요청 — 대부분 결국 `GROUP_BUY_FULL`로 거부될 요청까지 — 을
직렬화시킨다.

이 부하테스트는 이 병목을 실측하고, Redis 원자적 카운터를 DB 트랜잭션 앞단에
"빠른 거부(fast-fail)" 게이트로 추가했을 때 얼마나 개선되는지 before/after로
비교한다. (설계는 `docs`가 아니라 저장소 루트 계획 문서 참고 — Redis는 게이트
역할만 하고, 기존 DB 비관적 락 트랜잭션은 그대로 유지한다.)

## 재현 방법

```bash
# 1) MySQL, Redis 기동
docker compose up -d

# 2) 시드 데이터 투입 (before/after 실행 전 매번 재실행 — 카운터 리셋 포함, 재실행 안전)
docker compose exec -T mysql mysql -ugongguhan -pgongguhan1234! gongguhan < loadtest/seed.sql

# 3) 앱 기동 (JWT_SECRET을 k6 스크립트와 동일한 값으로 명시 지정)
#    HikariCP 풀 크기를 늘려서 커넥션 풀 대기가 아니라 실제 DB 락 경합을 측정한다.
cd backend
JWT_SECRET=loadtest-shared-secret-please-change \
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=80 \
./gradlew bootRun

# 4) k6 실행 (다른 터미널에서, before/after 동일한 커맨드로 재실행)
k6 run -e JWT_SECRET=loadtest-shared-secret-please-change -e RUN_LABEL=before \
  --summary-export=loadtest/results/before-summary.json \
  loadtest/k6/prepare-slot.js
```

`after` 실행 전에는 반드시 2번(seed.sql 재실행)을 다시 수행해서
`reserved_slots`/`current_participants`를 0으로 되돌린 뒤, `RUN_LABEL=after`,
`--summary-export=loadtest/results/after-summary.json`로 동일 커맨드를 재실행한다.

## 시나리오

- 공동구매 1건, `target_participants=50`
- 서로 다른 900명의 시드 사용자, 각자 정확히 1회씩만 요청 (재시도/중복 없음 →
  `DUPLICATE_PARTICIPATION`이 `GROUP_BUY_FULL`과 섞여 집계되는 것을 방지)
- k6 `shared-iterations` executor로 짧은 시간에 몰아서 요청 (지속 부하가 아니라
  순간 폭주 시나리오 — 실제 한정 수량 이벤트와 동일한 모양)
- 기대 결과: 성공(201) 약 50건, 거부(409 GROUP_BUY_FULL) 약 850건

## 측정 지표

- `http_req_duration` p50/p95/p99 (k6 기본 제공)
- `success_latency` / `reject_latency` (커스텀 Trend — 201/409 응답을 분리 측정)
- `unexpected_errors` (5xx, 타임아웃 등 — 0이어야 정상)

## 결과

<!-- before/after 실측 후 여기 표로 채운다. 가짜 수치를 미리 적지 않는다. -->

| 지표 | Before (DB 락만) | After (Redis 게이트) |
| --- | --- | --- |
| 처리량 (req/s) | TBD | TBD |
| p50 latency | TBD | TBD |
| p95 latency | TBD | TBD |
| p99 latency | TBD | TBD |
| reject_latency p95 | TBD | TBD |
| success_latency p95 | TBD | TBD |
| unexpected_errors | TBD | TBD |

(해석은 실측 후 작성)
