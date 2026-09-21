# B04 배송지 복원과 주문별 요청사항 계약 경계

이슈: S15P21B205-196. 기준 DevelopV2 a9fa9c1. 이 MR은 UI 선택 복원과 다른 공구 값 혼입을 수정한다. 주문별 요청사항을 실제 배송 DB까지 전달하는 것은 아래 후속 계약이며 완료로 표시하지 않는다.

## 문제와 선택 이유

비기본 배송지와 경비실 요청을 고른 후 결제 단계에서 뒤로 돌아오면 기본 주소와 빈 요청사항이 보였다. checkoutStore에는 선택이 남아 있었지만 ShippingForm이 항상 기본 주소와 빈 문자열로 초기화했다. 사용자가 주소를 다시 확인하지 않으면 다른 곳에 배송될 수 있으므로 기존 선택을 복원한다.

단순히 addressId만 복원하면 다른 공동구매의 주소나 이미 삭제한 주소를 가져올 수 있다. 따라서 groupBuyId가 일치하고 해당 배송지가 목록에 있을 때만 복원한다. 이전 선택 주소가 사라졌다면 기본 주소로 조용히 대체하지 않고 재선택을 요구한다. 배송지가 실시간 재조회에서 삭제되어도 제출 시 현재 목록에 존재하는지 다시 검사한다.

## 흐름과 구현

initialShipping 순수 함수가 저장 상태와 주소 목록으로 초기값을 정한다. 같은 공구이고 기존 선택이 있으면 주소와 요청사항을 복원한다. 사용자가 명시적으로 요청사항을 비운 null도 빈 값으로 유지하며 주소 기본 요청사항으로 되살리지 않는다. 새로운 공구는 기본 주소/첫 주소를 제안한다. 저장소에는 요청사항이 문자열로 남지만 SelectField 옵션에 없는 기존 문구도 표시할 수 있도록 현재 값을 옵션에 포함한다.

ShippingForm에는 공구 ID key를 주어 URL로 공구가 바뀔 때 이전 폼 상태가 이어지지 않게 했다. PaymentPage도 checkoutStore.groupBuyId와 화면 공구 ID가 다르면 주소를 전달하지 않는다. 직접 결제 URL로 다른 공구에 진입하여 이전 주소로 자리를 잡는 부수 문제를 막는다. 디자인·스타일 변경은 없다.

## 테스트

추가 5개는 비기본 주소 복원, 요청사항 없음 유지, 다른 공구 분리, 삭제된 주소의 명시적 재선택, 주소 없음이다. 이전 슬롯 수명 테스트를 포함한 프런트 133개, ESLint, TypeScript 포함 production build가 통과했다. 이는 함수·회귀 검사이며 실제 주문 생성과 배송 요청사항 저장 E2E 성공을 의미하지 않는다. 성능 최적화가 아니므로 테스트 실행 시간을 성능 개선으로 계산하지 않는다.

## 주문별 요청사항 전달 후속 계약

현재 frontend/src/entities/payment/model.ts PreparePaymentRequest는 method/addressId/cryptoAssetId만 전송한다. checkoutStore.deliveryRequest는 서버에 전달되지 않는다. backend의 PaymentService.completePayment는 ParticipationSnapshot의 수령인·전화·우편번호·주소를 참여에 복사하고 요청사항은 복사하지 않는다. UserAddress.deliveryRequest는 주소록 기본값이며 주문별 요청과 구분해야 한다.

완전한 계약은 다음 순서가 필요하다. (1) prepare 요청에 선택적인 deliveryRequest 필드 및 길이 제한을 둔다. (2) Payment의 최초 준비 요청 스냅샷에 고정하고 멱등키 본문 비교에도 포함한다. 생략 시 주소 기본값, 명시적 null 시 요청 없음 중 어떤 계약인지 구분해야 한다. (3) 모든 카드·카카오·코인 승인 경로에서 동일 스냅샷을 참여/배송 레코드로 넘긴다. (4) DeliveryResponse 및 판매자 주문 화면에서 그 값을 노출한다. (5) 주소록을 나중에 수정해도 과거 주문 요청이 바뀌지 않는 단위·트랜잭션·API 회귀를 추가한다.

이 작업은 PaymentService와 부모의 B17 멱등성/승인 스냅샷 수정에 걸치므로 이번 UI MR에서 임의로 주소록을 덮어쓰는 우회는 하지 않았다. B04의 화면 복원만 해결됐으며 전달 계약이 병합되기 전 전체 B04 완료라고 해석하지 않는다. 사용자는 B04 후속 문서와 최종 상태표를 함께 확인해야 한다.

학습 순서: ShippingForm 초기화 → initialShipping 테스트 → checkoutStore의 공구 경계 → PaymentPage 진입 방어 → 서버 prepare/completePayment 스냅샷 계약. 재실행: frontend에서 npm test, npm run lint, npm run build.
