# 과거 구매 이벤트 특징의 시점(당시-값) 일관성 (§9)

관련 인수인계: `HANDOFF-2026-09-22.md` §9. 추천 학습에 쓰는 **과거 구매(PURCHASE) 이벤트**의 특징이 "구매 당시값"이 아니라 "현재 상품/공구 값"으로 계산되던 시점(temporal) 불일치를 다룬다.

## 1. 증상

`RecommendationRepository.findTrainingEvents()`는 학습용 과거 이벤트를 두 소스에서 모은다.

- **소스 A — `recommendation_events`(노출/클릭/상세조회)**: 이벤트 발생 시점에 `recruitment_rate`·`discount_rate`·`deadline_urgency`를 스냅샷 컬럼으로 저장해 둔다. 시점 정합적이다.
- **소스 B — `participations`(PURCHASE)**: 저장된 스냅샷이 없어, 조회 시점에 `group_buys`·`products` 현재 엔티티를 조인해 특징을 계산한다.

소스 B의 `mapPurchaseEvent()`에서 `recruitment_rate = current_participants / target_participants`로 **현재 누적 참여자수**를 쓴다. 그 결과 **이미 모집이 끝난 과거 공구의 모든 구매가 ~100% 참여율에서 일어난 것으로 라벨**된다. 실제로는 참여율 10%일 때 산 구매가 학습에서는 "참여율 100%에서 구매"로 왜곡돼, 추천 모델이 "붐빌수록 산다"는 잘못된 신호를 학습한다. 이벤트의 `occurred_at`은 과거(`joined_at`)인데 특징만 현재값이라는 시점 불일치다.

## 2. 이미 수정된 것과의 경계

미래 이벤트 배제(193), null 이벤트(190), 모집기간 후보 필터(191)는 각각 *어떤 이벤트를 세는가(시간 필터)*, *입력 검증*, *현재 추천 후보 선별*의 문제였다. 이번 건은 *세는 이벤트의 특징이 언제 시점 값인가(값 해석)* 로 축이 다르며, 그 수정들은 `findTrainingEvents`의 participations 소스를 건드리지 않아 이 결함은 독립적으로 잔존했다.

## 3. 당시값 가용성

- **참여율 당시값**: `participations`를 `joined_at <= 해당 참여.joined_at`로 COUNT하면 그 구매 시점의 누적 참여수를 재집계할 수 있다. `target_participants`는 공구 생성 시에만 설정되는 불변 값이라 분모로 그대로 쓸 수 있다. → **재집계 가능.**
- **할인율 당시값**: `discount_rate = (products.price − group_buys.product_price) / products.price`. `group_buys.product_price`는 불변이지만 `products.price`(정가)는 `Product.update()`로 바뀔 수 있고, 구매 당시 정가는 어디에도 스냅샷으로 저장돼 있지 않다. → **재구성 불가.**
- **마감임박도**: 이미 과거 `occurredAt`(=`joined_at`)과 `recruit_end_at`으로 계산해 시점 정합적이다.

## 4. 수정

`findTrainingEvents()`의 PURCHASE 쿼리에서 `gb.current_participants` 대신 **당시 참여수를 상관 서브쿼리로 재집계**한다.

```sql
(SELECT COUNT(*) FROM participations pp
  WHERE pp.group_buy_id = part.group_buy_id
    AND pp.joined_at <= part.joined_at
    AND pp.status IN ('PAID','FULFILLED','SHIPPING','DELIVERED','CONFIRMED','SETTLED')
) AS participants_at_join
```

`mapPurchaseEvent()`는 `recruitment_rate`를 이 `participants_at_join / target_participants`로 계산한다.

- **할인율**은 그대로 둔다. 당시 정가가 저장돼 있지 않아, 저장되지 않은 당시값을 현재값으로 채운 뒤 "스냅샷"이라 부르지 않는다(인수인계 지침). 판매자가 정가를 바꾼 경우에만 왜곡되는 별개의 엣지이며, 정확한 해결은 공구 생성 시 정가를 스냅샷으로 저장하는 별도 변경이 필요하다(§6 범위 밖 후속).
- **마감임박도**는 이미 시점 정합적이라 변경하지 않는다.
- AI(`ai/`)는 백엔드가 계산해 보낸 값을 그대로 피처로 신뢰하므로(재조회하지 않음), 근원인 백엔드 소스 B만 고치면 학습·추천 경로 전체에 반영된다.

부수적으로, 소스 A(스냅샷) 쿼리의 1초 유예 컷오프를 MySQL 전용 `DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 SECOND)`에서 **UTC 기준으로 계산한 바인딩 파라미터**로 바꿨다. `last_event_at`도 UTC로 저장되므로 동작은 동일하고, `findTrainingEvents`를 H2 통합 테스트로 검증할 수 있게 이식성을 확보한다(기존에는 이 메서드가 H2로 시험된 적이 없었다).

## 5. 검증

- 재현/검증 테스트(`RecommendationPurchaseTemporalFeatureTest`): target 10인 공구에 서로 다른 `joined_at`으로 참여를 쌓고 `current_participants`를 최종 누적(8)으로 둔 뒤 `findTrainingEvents`를 호출한다. 이른 구매의 `recruitment_rate`가 **당시값 0.1**(1/10), 늦은 구매가 0.5(5/10)로 나오고, 현재 누적 오염값 0.8이 **아님**을 관측한다(수정 전이면 모두 0.8). 단독 구매 대조군도 당시값 0.1.
- 회귀: 기존 추천 테스트(`RecommendationCandidateWindow`/`Ownership`/`ServiceTest` 등) 무회귀. 전체 백엔드 회귀 **135 스위트 / 917개 통과 / 실패 0 / 오류 0 / 제외 3**(Redis 2·S3 1 환경 테스트), bootJar 성공. Java 21/H2.

## 6. 한계와 남은 범위

- **할인율 당시값**은 구매 시점 정가가 저장돼 있지 않아 재구성하지 않았다. 정확한 해결은 공구 생성 시 정가 스냅샷을 남기는 스키마 추가이며, 기존 데이터에는 소급 적용할 수 없다(판매자 정가 변경 시에만 발생하는 엣지라 이번 범위에서 제외).
- 이 수정은 학습 라벨의 시점 정합성을 개선할 뿐, 추천 품질 자체(사용자 만족도)를 입증하지 않는다. 이 회귀 통과는 정합성 검증이지 추천 성능 지표가 아니다.
- `participants_at_join` 상관 서브쿼리는 학습 배치(`findTrainingEvents`, LIMIT 있음) 경로라 핫패스가 아니다. 참여 데이터가 매우 커지면 `(group_buy_id, joined_at)` 인덱스 활용을 점검할 수 있다.

## 7. 재실행

worktree `ai-temporal-features`의 backend에서(JDK 21):

```
./gradlew.bat test --tests "*Recommendation*" --console=plain
```

전체 회귀:

```
./gradlew.bat --init-script ../docs/testing/full-regression-runtime.gradle test bootJar --console=plain
```
