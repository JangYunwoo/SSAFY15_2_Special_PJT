# 종료된 EXPIRED 슬롯의 재준비 차단 해소

이슈: S15P21B205-203. B02/B03 직렬 슬롯 수정 후 검토에서 발견한 후속 경계다.

## 왜 추가 수정했는가

서버 배치가 결제를 EXPIRED로 바꾼 후 프런트가 abort하면 상태 충돌 오류가 올 수 있다. 기존 어댑터는 오류 후 결제를 조회하여 FAILED만 이미 정리된 것으로 인정했다. EXPIRED는 자리가 종료된 상태인데도 held를 붙잡아 재시도와 수단 전환을 막았다. controller 내부 Promise 테스트만으로는 실제 API 어댑터의 이 분기 누락을 찾지 못했으므로 어댑터 자체를 테스트 대상으로 분리했다.

## 선택과 구현

모든 409를 무시하면 승인된 PAID나 아직 진행중인 PROCESSING도 해제 성공으로 오인한다. 원본 오류의 종류를 제한하고 서버 상세 조회에서 FAILED 또는 EXPIRED임을 확인할 때만 종료로 인정한다. detail 조회가 실패하면 불명확한 상태이므로 오류를 그대로 보존한다. 정상 abort 성공 시 불필요한 GET은 추가하지 않는다. releasePaymentSlot 함수는 usePaymentSlot의 실제 controller port로 연결된다.

## 테스트

실제 paymentApi 모듈 경계를 모킹한 9개 검사는 정상 해제, FAILED/EXPIRED, PAID/PROCESSING, 종료 오류 두 종류, 상세 조회 유실, abort 네트워크 유실을 구분한다. 전체 프런트 154개, ESLint, 타입 포함 production build가 통과했다. controller의 기존 직렬 처리·새 키·늦은 응답 테스트도 전체 회귀에 포함한다. 이 검증은 실제 서버 배치 타이밍 E2E가 아니라 프런트 API 어댑터 계약 검증이다.

기능 상태 수정이며 성능 향상 주장은 없다. DevelopV1·실제 결제 데이터는 변경하지 않는다. 학습 시 종료 상태 enum → releasePaymentSlot의 오류 분류 → controller의 held 보존 → 테스트 순서로 읽는다. 재실행은 frontend의 npm test, npm run lint, npm run build이다.
