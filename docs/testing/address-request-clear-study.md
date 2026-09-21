# B05 배송지 요청사항 삭제의 PATCH 계약

이슈: S15P21B205-197. 기준 DevelopV2 97c255e. 배포 DB 및 기존 주소 데이터는 변경하지 않는다.

## 재현과 원인

주소 수정 화면에서 배송 요청사항을 선택 없음으로 바꾸면 프런트는 deliveryRequest:null을 전송했다. 그러나 AddressUpdateRequest의 record는 필드가 아예 없는 JSON과 명시적 null을 모두 Java null로 만들었다. UserAddress.update는 null을 미변경으로 해석했다. 결과적으로 저장 성공 응답을 받아도 기존 요청사항이 남았다.

원인은 프런트 문자열 표시가 아니라 세 가지 명령(유지·변경·삭제)을 두 값(문자열·null)로 압축한 계약이다. 프런트에서 공백을 보내 삭제를 흉내내면 데이터가 공백으로 남고 다른 클라이언트도 같은 우회를 알아야 한다. null을 모두 삭제로 바꾸면 요청사항 없이 이름만 수정한 PATCH가 요청사항을 지워버린다. 두 방식 모두 제외했다.

## 구현 선택

AddressUpdateRequest만 record에서 작은 요청 객체로 바꾸고 Jackson의 deliveryRequest setter가 실제로 호출되었는지 기록한다. JSON에 필드가 없으면 호출되지 않아 present=false이고, 명시적 null이면 present=true/value=null이 된다. 나머지 필드는 기존 검증 어노테이션과 null=미변경 계약을 유지한다. 전체 PATCH 공통 프레임워크나 새 라이브러리를 도입할 필요가 없는 범위다.

서비스는 먼저 소유 주소를 조회하고 기존 update로 일반 필드를 수정한다. 그 후 hasDeliveryRequest가 참인 경우에만 replaceDeliveryRequest를 호출한다. 엔티티의 새 명령은 null도 대입하므로 명시적 삭제가 가능하다. JPA 관리 엔티티의 변경 감지가 트랜잭션 종료에 반영하며 별도의 직접 SQL은 없다. 요청 객체의 내부 presence 값에는 JsonIgnore를 적용하여 클라이언트가 임의로 주입하지 못하게 한다.

기존 Java 생성자 호출도 남겼다. 해당 생성자의 null은 과거처럼 미변경으로 취급한다. HTTP JSON의 명시적 삭제와 Java 내부 호출을 혼동하지 않도록 주석을 달았다. 앞으로 Java에서 삭제 요청을 구성하려면 setDeliveryRequest(null)을 명시적으로 호출하거나 HTTP 역직렬화 결과를 사용한다.

## 테스트 설계와 결과

실제 Jackson 3 JsonMapper로 JSON을 파싱한 뒤 실제 AddressService와 UserAddress를 연결하고 Repository만 모킹했다. 다음 6개를 추가했다: 필드 생략 유지, 명시적 null 삭제, 새 문자열 교체, 다른 주소 필드 보존, 내부 presence 플래그 주입 차단, 101자 요청사항 길이 검증. 기존 소유권 거절·기본 배송지 승격 테스트 6개도 함께 통과했다.

전체 백엔드 102개 테스트 클래스, 651개 중 648개 통과·실패/오류 0·Redis/S3 환경 제외 3개. Docker는 사용자 보류 상태이므로 제외를 통과로 바꾸지 않았다. 새 테스트는 실제 DB flush/조회 E2E가 아니라 JSON→서비스→엔티티 경계 검사다. 배포 브라우저 재검증과 기존 주소의 일괄 수정은 수행하지 않았다.

기능 정합성 수정이므로 응답시간 개선을 주장하지 않는다. record→객체 전환으로 필드 존재 여부를 보존한 이유가 핵심이며 코드 줄 수 감소를 목표로 삼지 않았다. 학습 순서는 프런트 AddressForm null 전달 → 요청 DTO setter → 서비스 presence 분기 → 엔티티 명령 → AddressRequestClearTest 순서다.

재실행: backend에서 gradlew.bat test --tests '*AddressRequestClearTest' --tests '*AddressServiceTest', 전체 회귀는 gradlew.bat test. 비밀 설정이나 금융 키는 필요하지 않다.
