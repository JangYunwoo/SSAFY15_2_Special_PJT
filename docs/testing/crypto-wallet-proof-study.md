# C03 코인 결제 지갑 소유권 증명 학습 기록

이슈 S15P21B205-204. 기준 DevelopV2 1d2ec48. V20 마이그레이션을 포함한다. 사용자는 백엔드/프런트 기능 버그 수정을 허용했으며 화면 스타일은 유지한다. 실제 지갑 개인키나 금융망 비밀값은 저장하지 않는다.

## 1. 기존 코드가 보장한 것과 보장하지 못한 것

CryptoTxVerificationService는 로그인 사용자의 결제인지 확인했다. CryptoTxRecordService는 체인에서 받은 영수증의 토큰, 수취 주소, 금액, 신고된 fromAddress를 확인했다. 이 중 어느 것도 'fromAddress를 신고한 로그인 사용자가 그 지갑을 실제로 제어한다'는 증거는 아니다. 블록체인의 거래 해시와 송신 주소는 공개돼 있다. 공격자는 다른 사람의 아직 등록되지 않은 동일 금액 거래를 보고 fromAddress까지 그대로 복사할 수 있다. txHash UNIQUE는 두 번 사용을 막지만 정당한 사용자보다 먼저 가져가는 행위를 막지 못한다.

users.wallet_address는 ERD/초기 SQL에서 최근 사용 MetaMask 주소라는 의미이고, 현 코드에는 서명으로 검증해서 등록하는 흐름이 없었다. 이 값과 문자열을 비교하는 것은 소유권 증명이 아니다. 빈 값 때문에 정상 사용자를 모두 막거나 검증되지 않은 문자열을 신뢰하게 되므로 선택하지 않았다.

## 2. 검토한 대안

브라우저가 eth_requestAccounts로 얻은 주소만 보내는 방법은 요청자가 그 문자열을 임의 생성할 수 있어 서버 증명이 아니다. 거래 해시의 송신자가 맞는지만 확인하는 것은 기존 문제와 같다. 결제별 온체인 메모/컨트랙트 함수로 사용자 식별자를 묶는 방법도 가능하지만 토큰 전송 계약 전체를 바꾸어야 한다. 이번에는 기존 ERC-20 전송을 유지하면서 송신자가 개인키를 제어한다는 오프체인 서명을 추가했다.

암호 연산을 직접 작성하지 않았다. 현재 프로젝트가 이미 사용하는 web3j 4.12.3의 `Sign.signedPrefixedMessageToKey`로 EIP-191 개인 메시지 서명을 복구하고 주소를 대조한다. MetaMask 공식 문서는 오프체인 사람이 읽는 메시지 용도로 personal_sign을 지원한다. 구현 근거는 [web3j 4.12.3 Message Signing](https://docs.web3j.io/4.12.3/advanced/message_signing/)과 [MetaMask Sign data](https://docs.metamask.io/metamask-connect/evm/guides/sign-data/)다.

## 3. challenge에 무엇을 묶었나

서버가 UUID 난수 nonce를 만들며 challengeId와 메시지에 같은 값을 넣는다. 메시지는 도메인, Gongguhan wallet ownership proof v1 용도, 로그인 사용자 ID, 결제 ID, 견적 ID, chain ID, 송신 주소, 거래 해시, UTC 만료 시각을 포함한다. 송금하거나 토큰 권한을 부여하는 서명이 아니라는 설명도 넣었다. 서명할 원문은 클라이언트가 임의 생성하지 않고 서버가 저장한 메시지를 그대로 사용한다. 외부 서비스에서 우연히 얻은 서명이나 다른 결제의 서명을 재사용할 수 없도록 문맥을 묶는다.

도메인은 기본 j15b205.p.ssafy.io이며 다른 독립 환경에서는 app.crypto.wallet-proof-domain을 설정한다. 사용자가 보낸 Host 헤더를 메시지 도메인으로 신뢰하지 않는다. 만료는 서버 현재시각+5분, 결제 슬롯 만료, 견적 만료 중 최소다. 따라서 증명만 오래 남아서 만료한 슬롯을 새로 인정하지 않는다.

## 4. 저장 구조와 JPA 선택

V20은 crypto_wallet_challenges 테이블을 추가한다. PK는 challenge_id, 사용자/결제/견적 FK, 체인/주소/해시/원문/만료/소비시각을 저장한다. 원본 ERD에 없던 보안 상태를 명시적으로 추가한 것이다. 사용자 지갑 주소 필드의 의미를 검증된 주소로 조용히 바꾸지 않았다.

CryptoWalletChallenge는 JPA 엔티티이며 repository의 findForUpdate는 PESSIMISTIC_WRITE를 사용한다. SQL 문자열을 서비스에 넣지 않았다. 마이그레이션의 DDL은 스키마를 만드는 목적이라 SQL 파일로 관리한다. 읽기 검증은 짧은 readOnly 트랜잭션, 실제 nonce 소비는 쓰기 트랜잭션이다.

## 5. 요청 순서와 원자성

CRYP-05 POST에서 본인 소유의 진행 중·미만료 코인 결제와 유효 견적을 확인하고 메시지를 발급한다. 프런트는 MetaMask의 선택 계정과 네트워크를 대조하고 UTF-8 메시지를 hex로 전달해 personal_sign을 받는다. 개인키는 지갑을 떠나지 않는다. 서명 거절 시 거래 등록 API를 호출하지 않는다.

CRYP-03에는 challengeId/signature를 필수로 받는다. 서버는 RPC 전에 사용자/결제/견적/주소/해시 바인딩과 만료, 서명 복구 주소를 확인한다. 통과하면 기존 체인 조회를 트랜잭션 밖에서 수행한다. 그 뒤 별도 빈 CryptoWalletProofService.recordVerified가 nonce 행을 잠그고 다시 검증한다. 결제 행도 잠가 상태/슬롯/중복 등록을 확인한다. nonce.consume와 기존 records.record의 견적 claim·거래·입금 기록을 같은 트랜잭션으로 묶는다. 기록 중 실패하면 nonce 소비도 롤백되므로 네트워크/일시 DB 오류 뒤 같은 요청으로 재시도할 수 있다.

nonce를 RPC 전에 무조건 소모하는 대안은 노드 오류만으로 사용자에게 매번 서명을 요구하고 정상 재시도를 막기 때문에 쓰지 않았다. 반대로 등록 후 별도 트랜잭션으로 소비하면 기록과 소비 사이의 장애로 재사용 상태가 남기 때문에 쓰지 않았다.

## 6. 동일 요청 재시도와 재사용 공격의 차이

성공 응답이 유실될 수 있다. 프런트 cryptoSubmission은 payment/quote/address/hash를 키로 서명 요청 본문을 메모리에 유지하며, 재전송은 같은 challengeId와 signature를 사용한다. Idempotency-Key도 challengeId로 유지한다. localStorage나 로그에는 서명을 보관하지 않는다. 내용이 달라지면 새 증명을 발급한다.

서버는 소비된 증명도 사용자와 모든 바인딩, 서명을 다시 확인한다. 저장된 crypto_payments가 정확히 같은 quote/chain/fromAddress/txHash이면 현재 상태만 돌려준다. 이미 기록된 거래의 응답 복구는 증명의 시간 만료 후에도 허용하지만, 새 거래를 등록하거나 다른 ID로 바꾸는 것은 허용하지 않는다. 서명 검증과 사전 조회 사이에 동일 요청이 먼저 커밋한 경우도 재확인 후 복구한다.

## 7. 프런트 동작과 유지한 디자인

기존 지갑 주소/해시 폼과 클래스/레이아웃을 유지했다. 안내 문구에 결제하기 단계의 소유권 서명이 필요함을 추가했다. 승인/서명 중 지갑·해시·자산·결제수단 변경을 막는다. MetaMask 송금 대기 중에도 수단이 바뀌어 다른 슬롯으로 결과가 들어가지 않도록 busy 상태를 상위와 공유한다. 견적 만료는 지갑 상호작용 전과 실제 송금 요청 직전에 다시 확인한다. 지갑에서 이미 전송한 거래를 앱이 철회할 수 있다는 뜻은 아니다.

## 8. 검증 설계

CryptoWalletProofIntegrationTest는 실제 JPA challenge/결제/견적 저장과 표준 서명을 사용한다. 테스트 키 BigInteger.ONE은 공개된 테스트 전용 값이며 실제 자금을 넣지 않는다. 유효 서명, 서로 다른 nonce, 사용자·결제·견적·주소·해시 변조, 다른 서명자, 원문 변조, 잘못된 서명 길이, 만료, 중간 체인 변경, 실패 시 실제 트랜잭션 nonce 롤백, 같은 요청 복구, 두 스레드가 같은 nonce로 동시에 등록하는 경우를 검사한다. 이 계층에서 하위 기록 서비스만 mock으로 두어 nonce 트랜잭션 경계를 격리한다.

CryptoProofBeforeRpcTest는 잘못된 증명이 체인/영속성 조회에 도달하지 않는 것을 확인한다. CryptoSignedSubmissionIntegrationTest는 하위 기록 서비스까지 실제로 연결하고 체인만 대체한다. 서명→등록→quote used→입금 행→nonce consumed가 커밋되는지와 같은 요청을 두 번 호출해도 RPC가 1회인지 확인한다. 실제 MetaMask 팝업을 자동으로 승인하거나 자금을 보내지 않는다.

프런트 walletProof.test는 personal_sign의 UTF-8 hex 인수, 계정/체인 불일치, 사용자 서명 거절, 재시도 본문 동일성, 새 결제의 새 증명, 만료 견적 송금 차단을 검증한다. 실제 확장 프로그램 대신 EIP-1193 provider mock을 사용한다. 실제 브라우저 지갑 확인과 테스트넷 end-to-end는 별도 사용자 검증이다.

## 9. 배포 및 검증 한계

이 보안 수정은 서명 없는 기존 클라이언트를 의도적으로 거부한다. V20 마이그레이션 후 프런트/백엔드를 함께 적용해야 한다. 현재 작업은 DevelopV2 MR이며 DevelopV1/배포 DB/서버는 변경하지 않는다. 기존 거래 이력을 삭제하거나 다시 승인하지 않는다. 새로운 증명 없이 과거 거래를 다른 결제에 연결하는 우회 API를 만들지 않았다.

지원 범위는 MetaMask EOA 개인 메시지 서명이다. 거래소 출금 주소의 개인키를 사용자가 제어하지 못하는 경우나 EIP-1271 스마트컨트랙트 지갑은 이 흐름으로 증명할 수 없다. 이런 출금은 송금 전에 사용하지 않도록 안내해야 하며 지원하려면 별도 표준 검증 계약이 필요하다. consumed 증명은 응답 복구를 위해 보관한다. 보관 기간/정리 정책과 대량 발급 제한은 운영 정책으로 별도 결정해야 한다.

H2 회귀는 운영 MySQL 실행 계획·실제 Redis·실제 체인 재조직을 검증하지 않는다. Docker Redis/S3 테스트는 사용자 보류 요청대로 미실행이다. 본 변경은 인증/정합성 보완이므로 처리 성능 향상이나 추천 정확도 향상을 주장하지 않는다.

## 10. 재실행

backend에서 Java 21로 `./gradlew.bat test --tests '*Crypto*Proof*' --tests '*CryptoSignedSubmissionIntegrationTest' --console=plain`을 실행하고, 전체는 `./gradlew.bat test --console=plain`이다. frontend는 `npm test -- --run`, `npm run typecheck`, `npm run lint`, `npm run build`를 실행한다. 최종 실제 결과는 이 문서 하단과 MR 설명에 기록한다. 학습 순서는 공격 가능성 → challenge 메시지 구성 → 표준 서명 복구 → nonce 트랜잭션 → 통신 재시도 테스트가 적합하다.

## 11. 실제 최종 결과

2026-09-21 최종 백엔드 전체 698개 중 695개 통과, 3개 Redis/S3 환경 건너뜀, 실패/오류 0. 지갑 증명 통합 16개에는 증명 검증 이후 RPC 대기 구간에서 결제 취소 또는 슬롯 만료가 먼저 커밋된 순서도 포함한다. 이 두 경우 실제 기록 서비스는 호출되지 않고 nonce도 소비되지 않는다. 별도 RPC 이전 거부 1개, 실제 거래 기록 연결 1개까지 포함한다.

프런트 전체 153개 통과, typecheck/ESLint/프로덕션 빌드 통과. 최초 lint에서 catch 원인 보존 누락과 테스트 any 타입 2종을 발견해 cause/타입 선언으로 수정하고 재실행했다. 실제 브라우저 확장 프로그램이나 휴대전화의 서명 창, 실제 RPC 송금, 운영 MySQL V20 적용은 실행하지 않았다. 코드 테스트의 통과를 배포·실거래 검증으로 표기하지 않는다.
