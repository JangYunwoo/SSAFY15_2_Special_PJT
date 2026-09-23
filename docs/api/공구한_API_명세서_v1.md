# 공구한 API 명세서

| 항목 | 내용 |
| --- | --- |
| 문서 버전 | v1.2 |
| 기준 요구사항 | 공구한 요구사항 명세서 v4.0 |
| 최우선 기준 | 최신 ERDCloud, 31개 테이블 (`2oNmPznY5vTrAMDCi`) |
| API 형식 | REST / JSON / UTF-8 |
| Base URL | `/api/v1` |

## 0. 작성 기준과 선결 검토사항

본 명세서는 요구사항 명세서 v4.0의 사용자 동작을 API로 변환하되, 문서가 충돌하면 최신 ERDCloud의 정책과 데이터 구조를 우선한다.

다음 차이는 ERD v2 기준으로 확정하여 반영한다.

| 구분 | 요구사항 명세서 v4 | ERD v2 | 본 API 명세서 처리 |
| --- | --- | --- | --- |
| 결제 슬롯 | `SlotReservation` 독립 엔티티로 기술 | 별도 테이블 없음. `payments.status=PROCESSING`이 슬롯 역할 | 슬롯 전용 CRUD 없이 결제 준비 API로 통합 |
| 결제 실패 재시도 | 2분간 재시도 권한 유지 | 실패 즉시 슬롯 해제 | 실패 결제를 `FAILED`로 끝내고 재시도 시 새 결제 생성 |
| AI 분석 결과 | 분석 상태와 결과 저장 요구 | `ai_extractions` 제거 | 동기 API로 결과만 반환하고 판매자 검수 후 `products.ai_extracted` 기록 |
| 구매확정 후 반품 | 선환불·차기 정산 상계·회수 채권 지원 | 상계·채권 테이블 제거 | 배송 완료 7일 전까지 관리자 검토로 처리하며 정산은 `settlement_eligible_at` 이후에만 실행 |
| 도서산간 추가금 | 별도 우편번호·추가금 정책 요구 | 전용 컬럼·테이블 없음 | MVP API와 금액 계산에서 제외 |

계정은 `users`로 통합한다. 모든 사용자는 구매에 참여할 수 있고, 판매를 원하는 사용자는 휴대폰·1원 계좌 인증을 완료한 뒤 `seller_profiles`를 생성해 판매자 권한을 얻는다. 인증 시도와 결과는 `seller_verifications`에 보관한다.

또한 v4 본문의 3.5 제목 아래에는 `FR-CFM`이 배치되어 있고 3.6~3.12 본문이 누락되어 있다. 누락 영역은 v4의 추적 매트릭스와 v3 요구사항을 참고하되, 최종 동작과 저장 구조는 ERD v2로 확정했다.

## 1. 공통 규칙

### 1.1 인증과 권한

- 인증 헤더: `Authorization: Bearer {accessToken}`
- 역할: `USER`, `SELLER`, `ADMIN`
- 모든 일반 계정은 `USER` 권한을 가지며, 유효한 `seller_profiles`가 있는 계정만 `SELLER` API에 접근한다.
- 판매자도 같은 `users` 계정으로 구매 API를 사용할 수 있다. 관리자 API는 별도 `ADMIN` 인증 주체만 접근한다.
- `/auth/**`, 공개 공동구매 조회, 카테고리 조회를 제외한 API는 인증이 필요하다.
- 자원 소유권은 URL의 식별자뿐 아니라 인증 주체를 기준으로 다시 검증한다.

### 1.2 요청 헤더

| 헤더 | 적용 대상 | 설명 |
| --- | --- | --- |
| `Authorization` | 인증 API를 제외한 보호 API | JWT Access Token |
| `Idempotency-Key` | 결제 준비·승인, 환불 재처리, 정산 실행 | 동일 요청의 중복 처리를 차단하는 UUID |
| `Content-Type: application/json` | 일반 요청 | JSON 요청 |
| `Content-Type: multipart/form-data` | 이미지 업로드·AI 분석 | 파일 요청 |

### 1.3 공통 성공 응답

```json
{
  "success": true,
  "data": {},
  "meta": null,
  "traceId": "01J..."
}
```

목록 응답의 `meta`는 다음 형식을 사용한다.

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 120,
  "totalPages": 6
}
```

### 1.4 공통 오류 응답

```json
{
  "success": false,
  "error": {
    "code": "PAYMENT_SLOT_EXPIRED",
    "message": "결제 가능 시간이 만료되었습니다.",
    "fieldErrors": []
  },
  "traceId": "01J..."
}
```

### 1.5 데이터 형식

- 식별자: JSON Number 범위를 고려해 응답에서는 문자열 사용을 권장한다.
- KRW 금액: 소수점 없는 정수형 `long`을 사용한다.
- 디지털 자산 수량과 환율: 정밀도 손실을 막기 위해 문자열 Decimal로 전달한다.
- 일시: ISO-8601 UTC 형식으로 전달한다. 예: `2026-09-03T01:30:00Z`
- 상태값: 영문 대문자 Enum을 사용한다.
- 목록 기본값: `page=0`, `size=20`, 최대 `size=100`.

## 2. 계정 및 인증 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| AUTH-01 | POST | `/auth/signup` | 통합 사용자 회원가입 | Public | FR-ACC-01, 03, 04, 06 |
| AUTH-02 | POST | `/auth/login` | 통합 사용자 로그인 | Public | FR-ACC-02, 05, 08 |
| AUTH-03 | POST | `/auth/refresh` | Access Token 재발급 | Authenticated | FR-ACC-05 |
| AUTH-04 | POST | `/auth/logout` | 로그아웃 및 Refresh Token 폐기 | Authenticated | FR-ACC-08 |
| AUTH-05 | POST | `/auth/admins/login` | 관리자 로그인 | Public | 관리자 인증 |
| USR-01 | GET | `/users/me` | 사용자 프로필 조회 | USER | FR-ACC-09 |
| USR-02 | PATCH | `/users/me` | 사용자 프로필 수정 | USER | FR-ACC-09 |
| USR-03 | GET | `/users/me/addresses` | 배송지 목록 조회 | USER | FR-ACC-06 |
| USR-04 | POST | `/users/me/addresses` | 배송지 등록 | USER | FR-ACC-06 |
| USR-05 | PATCH | `/users/me/addresses/{addressId}` | 배송지 수정 | USER | FR-ACC-06 |
| USR-06 | DELETE | `/users/me/addresses/{addressId}` | 배송지 삭제 | USER | FR-ACC-06 |
| SEL-01 | POST | `/users/me/seller-verifications/phone/request` | 판매자 전환 휴대폰 인증 요청 | USER | FR-ACC-07 |
| SEL-02 | POST | `/users/me/seller-verifications/phone/confirm` | 휴대폰 인증 확인 | USER | FR-ACC-07 |
| SEL-03 | POST | `/users/me/seller-verifications/account/request` | 정산계좌 1원 인증 요청 | USER | FR-ACC-07 |
| SEL-04 | POST | `/users/me/seller-verifications/account/confirm` | 정산계좌 1원 인증 확인 | USER | FR-ACC-07 |
| SEL-05 | POST | `/users/me/seller-profile` | 인증 완료 후 판매자 전환 | USER | FR-ACC-07 |
| SEL-06 | GET | `/sellers/me` | 판매자 프로필·제재 상태 조회 | SELLER | FR-ACC-09, FR-PEN-11 |
| SEL-07 | PATCH | `/sellers/me` | 연락처·정산계좌·반품주소 수정 | SELLER | FR-ACC-09 |

### 사용자 회원가입 요청

```json
{
  "email": "buyer@example.com",
  "password": "Password!234",
  "nickname": "공구왕",
  "phone": "01012345678",
  "defaultAddress": {
    "recipientName": "홍길동",
    "recipientPhone": "01012345678",
    "zipcode": "06236",
    "address1": "서울특별시 강남구 ...",
    "address2": "101동 101호"
  }
}
```

### 로그인 응답

```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "actor": { "id": "101", "role": "USER", "isSeller": false, "name": "공구왕" }
  },
  "meta": null,
  "traceId": "01J..."
}
```

### 판매자 전환 요청

휴대폰과 정산계좌에 대해 `VERIFIED` 상태이며 만료되지 않은 인증 이력이 모두 있어야 한다. 전환 성공 시 같은 `users.user_id`를 `seller_profiles.seller_id`로 사용하고 `users.is_seller=true`를 함께 반영한다.

```json
{
  "businessName": "공구상회",
  "businessPhone": "01012345678",
  "ssafyAccountNo": "0012345678901234",
  "accountHolder": "홍길동",
  "returnAddress": {
    "zipcode": "06236",
    "address1": "서울특별시 강남구 ...",
    "address2": "101동 101호"
  }
}
```

## 3. 카테고리·상품·AI API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| CAT-01 | GET | `/categories` | 카테고리 트리 조회 | Public | FR-PRD-02 |
| PRD-01 | POST | `/sellers/products` | 상품 등록 | SELLER | FR-PRD-01~04, 08 |
| PRD-02 | GET | `/sellers/products` | 내 상품 목록 조회 | SELLER | FR-PRD-05 |
| PRD-03 | GET | `/sellers/products/{productId}` | 내 상품 상세 조회 | SELLER | FR-PRD-05 |
| PRD-04 | PATCH | `/sellers/products/{productId}` | 상품 수정 | SELLER | FR-PRD-06~07 |
| PRD-05 | DELETE | `/sellers/products/{productId}` | 상품 삭제 | SELLER | FR-PRD-06~07 |
| AI-01 | POST | `/sellers/products/ai-extraction` | 이미지 기반 상품 정보 추출 | SELLER | FR-AI-01~08, IR-05 |
| WDR-01 | GET | `/withdrawal-restriction-reasons` | 활성 청약철회 제한 사유 목록 조회 | SELLER | FR-PRD-04, FR-RTN-04 |
| WDR-02 | GET | `/admin/products/withdrawal-restrictions` | 제한 상품 검토 목록 조회 | ADMIN | ERD v2 `restriction_review_status` |
| WDR-03 | POST | `/admin/products/{productId}/withdrawal-restriction/review` | 제한 상품 승인·반려 | ADMIN | ERD v2 `restriction_review_status` |

### 카테고리 기준 데이터

CAT-01은 다음 20개 최상위 카테고리를 표의 순서대로 반환한다. 새 상품 등록과 AI 카테고리 이름 매핑에는 `active=true`인 기준 데이터만 사용한다. 과거 상품이 참조하는 이전 카테고리 행은 외래키와 거래 이력을 보존하기 위해 삭제하지 않고 비활성화한다.

| ID | 순서 | 이름 |
| ---: | ---: | --- |
| 10001 | 1 | 디지털기기 |
| 10002 | 2 | 생활가전 |
| 10003 | 3 | 가구/인테리어 |
| 10004 | 4 | 생활/주방 |
| 10005 | 5 | 유아동 |
| 10006 | 6 | 유아도서 |
| 10007 | 7 | 여성의류 |
| 10008 | 8 | 여성잡화 |
| 10009 | 9 | 남성패션/잡화 |
| 10010 | 10 | 뷰티/미용 |
| 10011 | 11 | 스포츠/레저 |
| 10012 | 12 | 취미/게임/음반 |
| 10013 | 13 | 도서 |
| 10014 | 14 | 티켓/교환권 |
| 10015 | 15 | e쿠폰 |
| 10016 | 16 | 가공식품 |
| 10017 | 17 | 건강기능식품 |
| 10018 | 18 | 반려동물용품 |
| 10019 | 19 | 식물 |
| 10020 | 20 | 기타 |

### 상품 응답

PRD-01~04의 상품 응답은 같은 형태를 사용한다. `images`는 표시 순서와 대표 이미지 여부를 잃지 않도록 문자열 배열이 아니라 객체 배열로 반환한다. `linkedGroupBuyId`와 `linkedGroupBuyStatus`는 수정·삭제 가능 여부를 화면에 미리 표시하기 위한 현재 활성 공동구매 정보이며, 연결 건이 없으면 둘 다 `null`이다.

```json
{
  "productId": "301",
  "sellerId": "201",
  "categoryId": "10001",
  "categoryName": "디지털기기",
  "name": "무선 블루투스 이어폰",
  "brand": "Example",
  "modelName": "EB-100",
  "color": "화이트",
  "description": "상품 상세 설명",
  "price": 129000,
  "aiExtracted": true,
  "withdrawalRestricted": false,
  "withdrawalRestrictionReasonId": null,
  "withdrawalRestrictionReason": null,
  "restrictionEvidenceUrl": null,
  "restrictionReviewStatus": "NONE",
  "status": "ACTIVE",
  "images": [
    {
      "imageId": "901",
      "imageUrl": "https://cdn.example.com/products/301/main.jpg",
      "sortOrder": 0,
      "thumbnail": true
    }
  ],
  "linkedGroupBuyId": null,
  "linkedGroupBuyStatus": null,
  "createdAt": "2026-09-11T02:00:00Z"
}
```

### AI 상품 정보 추출

AI 분석은 ERD v2에 따라 동기 요청으로 처리한다. 분석 결과를 메인 DB에 별도 저장하지 않으며, 판매자가 결과를 검수해 상품을 등록할 때만 `products.ai_extracted=true`를 기록한다.

요청은 `multipart/form-data`로 전송하며 `images`에 JPG 또는 PNG 파일을 전달한다. 분석 결과는 저장 확정 데이터가 아니며 판매자가 수정한 후 PRD-01로 등록해야 한다.

```json
{
  "success": true,
  "data": {
    "status": "COMPLETED",
    "fields": {
      "name": { "value": "무선 블루투스 이어폰", "confidence": 0.96 },
      "brand": { "value": "Example", "confidence": 0.91 },
      "categoryId": { "value": "12", "confidence": 0.84 },
      "modelName": { "value": "EB-100", "confidence": 0.77 },
      "color": { "value": "화이트", "confidence": 0.93 },
      "price": { "value": 129000, "confidence": 0.72 }
    },
    "warnings": ["가격은 판매자가 최종 확인해야 합니다."]
  },
  "meta": null,
  "traceId": "01J..."
}
```

## 4. 공동구매 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| GRP-01 | GET | `/group-buys` | 공개 공동구매 검색·목록 | Public | FR-JOI-01~04 |
| GRP-02 | GET | `/group-buys/{groupBuyId}` | 공동구매 상세·금액·진행률 조회 | Public | FR-JOI-05, FR-PEN-14 |
| GRP-03 | POST | `/sellers/group-buys/preview` | 생성 전 분담금·수수료·예상 정산 계산 | SELLER | FR-GRP-05~09, FR-AMT |
| GRP-04 | POST | `/sellers/group-buys` | 공동구매 생성 | SELLER | FR-GRP-01~10, 14 |
| GRP-05 | GET | `/sellers/group-buys` | 판매자 공동구매 목록 | SELLER | FR-GRP-11 |
| GRP-06 | GET | `/sellers/group-buys/{groupBuyId}` | 판매자 운영 상세 | SELLER | FR-GRP-11, FR-STA-02 |
| GRP-07 | PATCH | `/sellers/group-buys/{groupBuyId}` | 참여 전 모집 조건 수정 | SELLER | FR-GRP-12 |
| GRP-08 | POST | `/sellers/group-buys/{groupBuyId}/cancel` | 참여자가 없는 모집 건 취소 | SELLER | FR-GRP-13 |
| GRP-09 | GET | `/sellers/group-buys/{groupBuyId}/participants` | 결제 완료 참여자 현황 | SELLER | FR-STA-02~03 |
| GRP-10 | POST | `/sellers/group-buys/{groupBuyId}/withdrawal` | 참여 발생 후 판매자 귀책 판매 철회 | SELLER | FR-PEN-01~02, 07 |

### 공동구매 생성 요청

```json
{
  "productId": "301",
  "productPrice": 1000000,
  "targetCount": 20,
  "shippingFee": 4000,
  "recruitStartAt": "2026-09-05T00:00:00Z",
  "recruitEndAt": "2026-09-12T14:59:59Z"
}
```

### 금액 계산 응답

```json
{
  "productPrice": 1000000,
  "targetCount": 20,
  "unitShareAmount": 50000,
  "roundingSurplus": 0,
  "shippingFee": 4000,
  "platformFeeRate": 0.05,
  "estimatedPlatformFee": 50000,
  "estimatedSettlementAmount": 1030000,
  "feePolicy": "RATE"
}
```

`targetCount`는 2 이상이어야 한다. `unitShareAmount`는 원 단위로 올림하여 생성 시 확정하며 참여 취소나 반품으로 다시 계산하지 않는다. 배송비는 판매자가 입력하고, 플랫폼 수수료는 배송비를 제외한 확정 상품 분담금 합계의 5%를 원 단위로 버림한다.

공개 목록은 `keyword`, `categoryId`, `status`, `sort`, `page`, `size`를 쿼리 파라미터로 받는다. 판매 철회 API는 참여자가 존재하는 거래에만 사용하며, 사유를 필수로 받아 공동구매 취소, 전원 환불 개시, 판매자 위반 후보 생성을 하나의 업무 흐름으로 실행한다.

### 개인화 추천 실험 API

| ID | Method | URL | 기능 | 권한 |
| --- | --- | --- | --- | --- |
| REC-01 | GET | `/recommendations/group-buys?size=8` | 로그인 사용자의 개인화 공동구매 순위 | USER |
| REC-02 | POST | `/recommendations/events` | 노출·클릭·상세조회 행동을 세션 단위로 기록 | USER |
| REC-03 | POST | `/recommendations/sessions/end` | 탭 종료 시 세션 종료 표시 | USER |

`REC-02`는 `sessionId`와 최대 100개의 `events`를 받는다. 이벤트 유형은
`IMPRESSION`, `CLICK`, `DETAIL_VIEW`만 허용한다. 구매는 클라이언트가 보내지 않고
서버의 `participations`를 정답 데이터로 사용한다. 종료된 세션 또는 1초 이상
활동이 없는 세션만 모델 학습에 반영한다. 1초는 시연에서 순위 변화를 즉시
확인하기 위한 값이며, 운영 전환 시에는 30분 무활동 기준으로 조정할 예정이다.

`REC-01`은 브랜드·카테고리 선호, 모집률, 할인율, 마감 임박도를 입력으로 한
LightGBM Learning-to-Rank 순위를 반환한다. 한 세션에 함께 노출된 상품을 하나의
학습 그룹으로 사용하고, 노출·클릭/상세조회·구매를 각각 관련도 0·1·3으로 학습한다.
학습 데이터가 부족하면 명시된 초기 가중치로, AI 서버가 응답하지 않으면 모집률 기반
순서로 자동 대체한다. 최근 30일 안에 구매한 동일 상품은 재구매 피로를 줄이기 위해
점수를 낮춘다.

## 5. 결제 슬롯 및 공통 결제 API

별도의 슬롯 리소스를 외부에 노출하지 않는다. 결제 준비 시 `payments`에 `PROCESSING` 행을 생성하고, `slot_expires_at`까지 해당 사용자의 자리를 보장한다.

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| PAY-01 | POST | `/group-buys/{groupBuyId}/payments/prepare` | 금액 검증·슬롯 선점·PROCESSING 결제 생성 | USER | FR-JOI-06~10, FR-SLT-01~11, FR-PAY-01~09 |
| PAY-02 | GET | `/payments/{paymentId}` | 결제·슬롯·참여 생성 결과 조회 | USER | FR-PAY-03~06, FR-STA-01 |
| PAY-03 | POST | `/payments/{paymentId}/abort` | 사용자 이탈 시 PROCESSING 결제 종료·슬롯 해제 | USER | FR-SLT-09 |
| PAY-04 | GET | `/users/me/payments` | 사용자 결제 이력 조회 | USER | FR-STA-01 |

### 결제 준비 요청

```json
{
  "method": "KAKAOPAY",
  "addressId": "501",
  "cryptoAssetId": null,
  "deliveryRequest": "문 앞에 놓아주세요"
}
```

필수 헤더: `Idempotency-Key`.

`deliveryRequest`는 최대 100자의 일회성 요청사항이다. 생략/`null`은 선택 주소록의 기본 요청을 사용하고,
빈 문자열은 요청 없음으로 확정한다. 주소록 자체는 수정하지 않는다. 최초 준비 시 수령인·전화번호·우편번호·주소·요청사항을
결제에 불변 스냅샷으로 저장하며, 이후 주소록 수정/삭제는 이 주문에 영향을 주지 않는다.
같은 멱등키 또는 활성 슬롯 재사용은 원본 요청사항까지 일치해야 하며, 변경된 요청은 기존 슬롯 종료 후 새 키로 준비한다.
참여 상세 및 판매자 배송 목록 응답의 `deliveryRequest`는 이 스냅샷에서 복사한 값이다(기존 주문은 `null`일 수 있다).

### 결제 준비 응답

```json
{
  "paymentId": "9001",
  "groupBuyId": "701",
  "status": "PROCESSING",
  "method": "KAKAOPAY",
  "amount": {
    "shareAmount": 50000,
    "shippingFee": 4000,
    "totalAmount": 54000,
    "currency": "KRW"
  },
  "slotExpiresAt": "2026-09-03T01:35:00Z",
  "remainingSeconds": 300
}
```

### 원자성 규칙

1. 공동구매가 `RECRUITING`인지 확인한다.
2. `paid_count + 유효한 PROCESSING 결제 수 < target_count`인지 잠금 범위에서 확인한다.
3. 동일 구매자의 유효 슬롯 또는 결제 완료 참여가 없는지 확인한다.
4. `payments(PROCESSING)`를 생성한다.
5. 외부 결제 호출은 DB 트랜잭션 밖에서 수행한다.
6. 외부 승인 검증 성공 후 짧은 트랜잭션에서 `PAID`, `participations`, `escrows(HELD)`, 참여 인원을 함께 확정한다.

## 6. 카드·카카오페이·디지털 자산 결제 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| CARD-01 | POST | `/payments/{paymentId}/card/approve` | 카드 승인 및 서버 검증 | USER | FR-PAY-11, 13~14, IR-01 |
| KAKA-01 | POST | `/payments/{paymentId}/kakaopay/ready` | 카카오페이 Ready 요청 | USER | FR-PAY-12~13, IR-02 |
| KAKA-02 | POST | `/payments/kakaopay/approve` | `pg_token`으로 Approve 후 참여 확정 | USER | FR-PAY-12~13, IR-02 |
| KAKA-03 | POST | `/payments/{paymentId}/kakaopay/cancel-flow` | 사용자 결제 취소 화면 복귀 처리 | USER | FR-SLT-09 |
| KAKA-04 | POST | `/payments/{paymentId}/kakaopay/fail-flow` | 결제 실패 화면 복귀 처리 | USER | FR-PAY-05 |
| CRYP-01 | GET | `/crypto/assets` | 지원 디지털 자산 목록 조회 | USER | FR-PAY-27, 32 |
| CRYP-02 | POST | `/payments/{paymentId}/crypto/quote` | 환율·Buffer 적용 Quote 생성 | USER | FR-PAY-21~25, 28~30, IR-03 |
| CRYP-03 | POST | `/payments/{paymentId}/crypto/transactions` | 사용자가 제출한 트랜잭션 해시 등록 | USER | FR-PAY-26, 31, IR-04 |
| CRYP-04 | GET | `/payments/{paymentId}/crypto/status` | 입금·컨펌·매칭 상태 조회 | USER | FR-PAY-25~26, IR-04 |
| CRYP-05 | POST | `/payments/{paymentId}/crypto/wallet-challenge` | 송신 지갑 소유권 증명 메시지 발급 | USER | C03 보안 보완 |

### 카드 승인 요청

```json
{
  "cardNumber": "CARD_NUMBER_FROM_SECURE_INPUT",
  "cvc": "CVC_FROM_SECURE_INPUT"
}
```

카드번호와 CVC는 SSAFY 카드 결제 API 호출에만 사용하며 저장하거나 로그로 남기지 않는다. DB에는 마스킹된 카드번호만 저장한다.

카드 인증정보는 외부 승인 요청에만 사용하고 DB·애플리케이션 로그·오류 로그에 저장하지 않는다. 실제 SSAFY 금융망 요청 필드는 별도 외부 연동 명세에서 매핑한다.

### 카카오페이 Ready 응답

```json
{
  "paymentId": "9001",
  "tid": "T1234567890",
  "nextRedirectPcUrl": "https://online-pay.kakaopay.com/mockup/v1/...",
  "expiresAt": "2026-09-03T01:35:00Z"
}
```

### 카카오페이 Approve 요청

```json
{
  "paymentId": "9001",
  "pgToken": "sample-pg-token",
  "state": "signed-one-time-state"
}
```

`state`는 결제 ID, 구매자 ID, 만료시각을 서명한 일회용 값이어야 한다. 승인 성공 응답을 받더라도 결제 금액·TID·주문 식별자를 서버에서 재검증한 후 참여를 생성한다.

### 카카오페이 취소·실패 복귀 요청 (KAKA-03 / KAKA-04)

두 POST 모두 요청 본문이 필수다. 프런트는 복귀 URL의 state를 그대로 전달한다.

```json
{
  "state": "signed-one-time-state"
}
```

서버는 서명·만료·로그인 사용자·경로의 결제 ID를 확인한 후, 저장된 Ready nonce와 사용 여부를 잠금 안에서 검증한다. 검증 성공 시 슬롯 종료와 state 소비를 같은 트랜잭션에서 처리한다. 누락·공백·잘못된 JSON·위변조/재사용은 400, 다른 사용자/결제의 state는 403, 만료는 409로 거부한다. 유효한 서명이라도 Ready 기록이 없으면 404로 거부한다. 거부된 콜백으로 슬롯을 해제하지 않는다.

본문 없는 예전 프런트와 호환되지 않으므로 프런트·백엔드를 함께 적용한다. 취소 콜백은 환불 API가 아니며 승인 완료 건을 환불하거나 실패 상태로 바꾸지 않는다. 외부 결제사에서 돌아오는 브라우저 GET 이동은 유지한다. state/pg_token은 로그에 남기지 않는다.

### 디지털 자산 Quote 응답

```json
{
  "quoteId": "11001",
  "asset": "USDT",
  "chainId": 80002,
  "krwAmount": 54000,
  "marketRate": "1380.12000000",
  "bufferRate": "0.003000",
  "appliedRate": "1384.26036000",
  "assetAmount": "39.010375921000000000",
  "depositAddress": "0x...",
  "requiredConfirmations": 3,
  "expiresAt": "2026-09-03T01:40:00Z"
}
```

### 코인 송신 지갑 소유권 증명 (C03)

CRYP-03 전 CRYP-05에 `quoteId`, `fromAddress`(0x+40자리), `txHash`(0x+64자리)를 POST한다. 응답 data는 `challengeId`, `message`, `expiresAt`, `chainId`다. message는 서버가 생성한 nonce와 도메인/용도, 로그인 사용자, 결제·견적·체인·송신 지갑·거래 해시·만료를 포함한다. 만료는 5분, 슬롯 만료, 견적 만료 중 가장 이른 시각이다.

프런트는 선택한 MetaMask 계정과 체인을 확인한 다음, message를 UTF-8 hex로 바꾸어 `personal_sign(messageHex, fromAddress)`한다. 서버에 개인키를 전송하지 않는다. CRYP-03 본문은 기존 세 필드에 `challengeId`, `signature`(0x+130자리)를 추가하며 두 값은 필수다. 서명은 새로운 송금이나 토큰 권한 승인이 아니라 이미 송금한 거래를 해당 사용자 결제에 귀속시키는 증명이다.

서버는 EIP-191 표준 서명 복구로 송신 지갑 소유권을 검증하고, 기존 체인 영수증의 송신자/수신자/금액/토큰 대조도 계속 수행한다. nonce 소비와 거래 기록은 같은 트랜잭션이다. 실패하면 nonce도 롤백된다. 동일 서명·동일 본문 재전송은 이미 기록된 정확히 같은 거래의 상태만 반환하고 RPC/결제를 재실행하지 않는다. 다른 사용자·결제·견적·주소·해시·변조 서명은 403, 누락/형식 오류/미소비 증명 만료는 400이다. 종료된 결제·견적에 대한 기존 409 계약은 유지한다.

서명 없는 예전 클라이언트와 호환되지 않으므로 V20 마이그레이션 및 프런트/백엔드를 함께 적용한다. 거래소가 관리하는 출금 주소나 스마트컨트랙트 지갑(EIP-1271)은 현재 지원하지 않는다. MetaMask EOA의 송신 계정으로 서명할 수 있는 거래만 등록한다. 전송 해시만 복사해서 결제하는 이전 방식은 의도적으로 차단한다.

## 7. 참여·구매확정·이의제기 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| PAR-01 | GET | `/users/me/participations` | 내 참여 목록·통합 상태 조회 | USER | FR-JOI-11, FR-STA-01 |
| PAR-02 | GET | `/users/me/participations/{participationId}` | 참여 상세·상태 이력 조회 | USER | FR-STA-01, 04~05 |
| PAR-03 | POST | `/users/me/participations/{participationId}/cancel` | 모집 완료 전 참여 취소·환불 개시 | USER | FR-JOI-12~13, FR-RFD-02 |
| CFM-01 | POST | `/users/me/participations/{participationId}/confirm` | 배송완료 건 수동 구매확정 | USER | FR-CFM-01~08 |
| CFM-02 | POST | `/users/me/participations/{participationId}/disputes` | 미배송·상품 불일치 이의제기 | USER | FR-CFM-09~10 |

### 구매확정 요청

```json
{
  "confirmed": true
}
```

구매확정은 `DELIVERED` 상태에서만 가능하며 취소할 수 없다. 반품 또는 이의제기가 진행 중이면 `409 CONFLICT`를 반환한다.

## 8. 에스크로 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| ESC-01 | GET | `/users/me/participations/{participationId}/escrow` | 참여자 에스크로 상태 조회 | USER | FR-ESC-04, 07 |
| ESC-02 | GET | `/sellers/group-buys/{groupBuyId}/escrows` | 판매자 공동구매별 보관 내역·합계 조회 | SELLER | FR-ESC-04, 07~08 |
| ESC-03 | GET | `/admin/escrows/summary` | 전체 보관액·정산대기액 집계 | ADMIN | FR-ADM-12 |

에스크로 상태 변경은 결제·환불·정산 유스케이스 내부에서만 수행하고, 일반 사용자가 상태를 직접 변경하는 API는 제공하지 않는다.

## 9. 배송 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| DLV-01 | GET | `/sellers/group-buys/{groupBuyId}/deliveries` | 참여자별 배송 목록·송장 등록률 조회 | SELLER | FR-DLV-01~04, 07 |
| DLV-02 | PUT | `/sellers/deliveries/{deliveryId}/invoice` | 택배사·송장번호·발송일 등록 | SELLER | FR-DLV-01~02, 05, 08 |
| DLV-03 | PATCH | `/sellers/deliveries/{deliveryId}/status` | 참여자별 배송 상태 변경 | SELLER | FR-DLV-03, 09, 13 |
| DLV-04 | PATCH | `/sellers/group-buys/{groupBuyId}/deliveries/status` | 배송 상태 일괄 변경 | SELLER | FR-DLV-10 |
| DLV-05 | GET | `/users/me/participations/{participationId}/delivery` | 사용자 배송 상태·송장 조회 | USER | FR-DLV-04, 11~12 |

### 송장 등록 요청

```json
{
  "carrierCode": "CJ",
  "trackingNumber": "123456789012",
  "shippedAt": "2026-09-10T02:00:00Z"
}
```

모집 완료 상태에서만 등록할 수 있고 동일 송장번호 중복 등록을 거부한다. 실시간 택배사 API는 호출하지 않는다.

## 10. 반품 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| RTN-01 | POST | `/users/me/participations/{participationId}/returns/preview` | 반품 가능 여부·예상 환불액 계산 | USER | FR-RTN-01~06, 21~26 |
| RTN-02 | POST | `/users/me/participations/{participationId}/returns` | 반품 신청 | USER | FR-RTN-11~13, 18 |
| RTN-03 | GET | `/users/me/returns` | 사용자 반품 목록 조회 | USER | FR-RTN-19 |
| RTN-04 | GET | `/users/me/returns/{returnId}` | 사용자 반품 상세·이력 조회 | USER | FR-RTN-19 |
| RTN-05 | GET | `/sellers/returns` | 판매자 반품 목록 조회 | SELLER | FR-RTN-19 |
| RTN-06 | POST | `/sellers/returns/{returnId}/approve` | 반품 승인 | SELLER | FR-RTN-14~15 |
| RTN-07 | POST | `/sellers/returns/{returnId}/reject` | 사유를 포함한 반품 거부 | SELLER | FR-RTN-14 |
| RTN-08 | POST | `/sellers/returns/{returnId}/pickup-complete` | 수거 완료 확인·환불 개시 | SELLER | FR-RTN-17 |
| RTN-09 | POST | `/users/me/returns/{returnId}/disputes` | 반품 거부 이의제기 | USER | FR-RTN-16 |
| RTN-10 | GET | `/admin/returns/reviews` | 이의제기·구매확정 후 반품 검토 목록 | ADMIN | FR-ADM-09, ERD v2 `admin_review_required` |
| RTN-11 | POST | `/admin/returns/{returnId}/resolve` | 관리자 검토 승인·기각 | ADMIN | FR-ADM-09, ERD v2 `forced_by` |

### 반품 미리보기 요청

```json
{
  "reasonType": "CHANGE_OF_MIND",
  "detailReason": "단순 변심"
}
```

### 반품 미리보기 응답

```json
{
  "eligible": true,
  "applicationDeadline": "2026-09-20T14:59:59Z",
  "paymentAmount": 54000,
  "refundableShareAmount": 50000,
  "refundableOriginalShippingFee": 0,
  "returnShippingFee": 8000,
  "refundAmount": 42000,
  "shippingFeeBearer": "BUYER"
}
```

단순변심이면 최초 배송비는 환불하지 않고 반품 배송비는 공동구매에 저장된 배송비의 2배로 계산한다. 판매자 귀책이면 최초 배송비를 포함해 전액 환불하고 반품 배송비는 판매자가 부담한다.

반품 신청과 구매확정의 동시 실행은 참여 행 잠금 또는 낙관적 버전 검증으로 하나만 성공시킨다. 이미 수동 구매확정된 건이라도 `settlement_eligible_at` 이전이면 반품을 접수하되 `postConfirm=true`, `adminReviewRequired=true`로 생성하여 관리자 검토 대상으로 보낸다.

## 11. 환불 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| RFD-01 | GET | `/users/me/refunds` | 내 환불 목록 조회 | USER | FR-RFD-07~10 |
| RFD-02 | GET | `/users/me/refunds/{refundId}` | 환불 상세·처리 결과 조회 | USER | FR-RFD-07~10 |
| RFD-03 | GET | `/admin/refunds` | 실패 포함 환불 목록 조회 | ADMIN | FR-RFD-09, FR-ADM-08 |
| RFD-04 | POST | `/admin/refunds/{refundId}/retry` | 실패 환불 재처리 | ADMIN | FR-RFD-09, FR-ADM-08 |

환불은 원 결제수단을 우선한다. 카드 청구 확정 등으로 승인 취소가 불가능한 경우 SSAFY 계좌이체 환불로 전환할 수 있다. 재처리 API에는 `Idempotency-Key`가 필요하다.

## 12. 판매자 정산 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| STL-01 | GET | `/sellers/settlements` | 판매자 정산 목록 조회 | SELLER | FR-STL-06~10, 14 |
| STL-02 | GET | `/sellers/settlements/{settlementId}` | 정산 상세·포함/제외 참여 건 조회 | SELLER | FR-STL-07~08 |
| STL-03 | GET | `/admin/settlements` | 전체 정산 목록·집계 조회 | ADMIN | FR-STL-12~14 |
| STL-04 | POST | `/admin/settlements/{settlementId}/execute` | 정산 계좌이체 실행 | ADMIN | FR-STL-01~05, 12 |
| STL-05 | POST | `/admin/settlements/{settlementId}/hold` | 분쟁 사유 정산 보류 | ADMIN | FR-STL-13 |
| STL-06 | POST | `/admin/settlements/{settlementId}/release-hold` | 정산 보류 해제 | ADMIN | FR-STL-13 |
| STL-07 | POST | `/admin/settlements/{settlementId}/retry` | 실패 정산 재처리 | ADMIN | FR-ADM-08 |

### 정산 상세 응답

```json
{
  "settlementId": "15001",
  "groupBuyId": "701",
  "sellerId": "201",
  "status": "PENDING",
  "confirmedCount": 19,
  "shareAmountTotal": 950000,
  "shippingFeeTotal": 76000,
  "roundingSurplus": 0,
  "platformFeeRate": 0.05,
  "platformFee": 47500,
  "netAmount": 978500,
  "items": [
    { "participationId": "801", "included": true, "amount": 54000 },
    { "participationId": "820", "included": false, "excludedReason": "RETURNED" }
  ]
}
```

정산 실행은 구매확정, `settlement_eligible_at <= now`, 에스크로 상태, 정산 항목 중복 포함 여부를 다시 검증한다. 실제 이체 호출과 DB 트랜잭션을 길게 묶지 않고 기관거래고유번호를 사용해 재처리 안전성을 확보한다.

## 13. 판매자 제재 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| PEN-01 | GET | `/sellers/me/penalties` | 판매자 위반 이력·누적 횟수 조회 | SELLER | FR-PEN-09~12 |
| PEN-02 | GET | `/admin/penalties` | 위반 후보·확정·기각 목록 조회 | ADMIN | FR-PEN-03~06, FR-ADM-10 |
| PEN-03 | GET | `/admin/penalties/{penaltyId}` | 위반 후보 근거 조회 | ADMIN | FR-PEN-06 |
| PEN-04 | POST | `/admin/penalties/{penaltyId}/confirm` | 위반 확정·누적·필요 시 정지 | ADMIN | FR-PEN-06~10 |
| PEN-05 | POST | `/admin/penalties/{penaltyId}/dismiss` | 위반 후보 기각 | ADMIN | FR-PEN-06 |
| PEN-06 | POST | `/admin/penalties/{penaltyId}/revoke` | 확정 위반 취소 | ADMIN | FR-PEN-13 |

3회 누적 시 신규 상품 등록과 공동구매 생성을 차단하되 기존 거래의 배송·반품·정산 처리는 허용한다.

## 14. 알림 API

배송 알림은 제공하지 않지만 모집 완료·실패, 환불, 정산, 제재 등 플랫폼 거래 알림은 제공할 수 있다.

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| NOTI-01 | GET | `/notifications` | 내 알림 목록 조회 | Authenticated | FR-CLS-06, FR-PEN-09 |
| NOTI-02 | GET | `/notifications/unread-count` | 읽지 않은 알림 수 조회 | Authenticated | FR-CLS-06 |
| NOTI-03 | POST | `/notifications/{notificationId}/read` | 알림 읽음 처리 | Authenticated | FR-CLS-06 |

## 15. 관리자·정책·운영 API

| ID | Method | URL | 기능 | 권한 | 요구사항 |
| --- | --- | --- | --- | --- | --- |
| ADM-01 | GET | `/admin/group-buys` | 전체 공동구매·거래 상태 검색 | ADMIN | FR-ADM-01 |
| ADM-02 | GET | `/admin/group-buys/{groupBuyId}` | 거래·결제·배송·환불·정산 종합 조회 | ADMIN | FR-ADM-01, 08 |
| ADM-03 | GET | `/admin/policies` | 정책값 전체 조회 | ADMIN | FR-ADM-02~07 |
| ADM-04 | PATCH | `/admin/policies/{policyKey}` | 정책값 변경·감사로그 기록 | ADMIN | FR-ADM-02~07, 13 |
| ADM-05 | GET | `/admin/external-api-logs` | 외부 금융 API 호출 결과 조회 | ADMIN | NFR-OP-01 |
| ADM-06 | POST | `/admin/jobs/{jobName}/run` | 모집 종료 등 배치 수동 재실행 | ADMIN | FR-CLS-05, NFR-OP-02~03 |
| ADM-07 | GET | `/admin/audit-logs` | 관리자 정책·상태 변경 감사로그 조회 | ADMIN | FR-ADM-13, ERD v2 `audit_logs` |

정책 키 예시는 다음과 같다.

- `PLATFORM_FEE_RATE`
- `CARD_SLOT_TTL_SECONDS`
- `KAKAOPAY_SLOT_TTL_SECONDS`
- `CRYPTO_QUOTE_TTL_SECONDS`
- `CRYPTO_BUFFER_RATE`
- `AUTO_CONFIRM_DAYS`
- `RETURN_APPROVAL_BUSINESS_DAYS`
- `SHIPPING_DEADLINE_BUSINESS_DAYS`

## 16. 주요 오류 코드

| HTTP | 코드 | 발생 조건 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 필수값 누락·형식 오류 |
| 401 | `AUTHENTICATION_REQUIRED` | 토큰 없음·만료 |
| 403 | `ROLE_ACCESS_DENIED` | 역할이 다른 API 접근 |
| 403 | `RESOURCE_ACCESS_DENIED` | 타인의 주문·배송·정산 접근 |
| 403 | `SELLER_SUSPENDED` | 정지 판매자의 등록·생성 요청 및 기존 공동구매 신규 결제 준비·재사용·승인 진입 (2026-09-23 정책 확정, 기존 거래 조회·취소·환불은 유지) |
| 404 | `RESOURCE_NOT_FOUND` | 대상 자원 없음 |
| 409 | `GROUP_BUY_NOT_RECRUITING` | 모집 중이 아닌 공동구매 참여 |
| 409 | `GROUP_BUY_FULL` | 참여자와 유효 결제 슬롯이 목표 인원 도달 |
| 409 | `DUPLICATE_PARTICIPATION` | 동일 구매자의 중복 참여 |
| 409 | `PAYMENT_ALREADY_PROCESSED` | 완료·실패 결제 재승인 |
| 409 | `PAYMENT_SLOT_EXPIRED` | 슬롯 만료 후 승인 도달 |
| 409 | `SLOT_RESERVATION_CONFLICT` | 슬롯 예약 낙관적 락 충돌 재시도 소진 |
| 409 | `INVALID_STATE_TRANSITION` | 허용되지 않은 상태 전이 |
| 409 | `DUPLICATE_TRACKING_NUMBER` | 동일 택배사·송장번호 중복 등록 |
| 409 | `PURCHASE_CONFIRM_RETURN_CONFLICT` | 구매확정과 반품 신청 충돌 |
| 422 | `RETURN_NOT_ELIGIBLE` | 반품 기간·제한 사유 위반 |
| 422 | `QUOTE_EXPIRED` | 만료된 디지털 자산 Quote |
| 422 | `CRYPTO_AMOUNT_MISMATCH` | 입금액 과소·과다 또는 자산 불일치 |
| 422 | `SSAFY_MEMBER_NOT_LINKED` | SSAFY 금융망 회원(userKey) 미연동 |
| 502 | `EXTERNAL_PAYMENT_ERROR` | 외부 결제기관 오류 |
| 503 | `EXTERNAL_SERVICE_UNAVAILABLE` | AI·시세·체인·금융망 일시 장애 |

## 17. 상태 전이 API 검증 규칙

| 대상 | 허용 전이 | API 검증 |
| --- | --- | --- |
| Payment | `PROCESSING → PAID / FAILED / EXPIRED` | 만료시각·멱등키·외부 승인 결과 검증 |
| Payment | `PAID → REFUNDED` | 환불 완료 후에만 변경 |
| GroupBuy | `RECRUITING → FULFILLED / FAILED / CANCELED` | 결제 완료 참여 수와 모집 종료시각 검증 |
| Participation | `PAID → FULFILLED → SHIPPING → DELIVERED → CONFIRMED → SETTLED` | 이전 상태·권한·시각 검증 |
| Participation | 정상 경로 → `CANCELED / RETURNING / RETURNED / REFUNDING / REFUNDED` | 모집·배송·반품·환불 상태 검증 |
| Escrow | `HELD → RELEASED / REFUNDED` | 구매확정 또는 환불 근거 검증 |
| Delivery | `READY → SHIPPING → DELIVERED` | 송장 중복·판매자 소유권 검증 |
| ReturnRequest | `REQUESTED → APPROVED → PICKED_UP → REFUNDED` | 기한·사유·처리주체 검증 |
| ReturnRequest | `REQUESTED → REJECTED → DISPUTED` | 거부 사유와 구매자 이의 검증 |
| Settlement | `PENDING → PARTIAL / COMPLETED / HOLD` | 포함 항목·금액·중복 정산 검증 |

## 18. 외부 연동과 비동기 처리

| 처리 | 방식 | 주기·트리거 | 중복 방지 기준 |
| --- | --- | --- | --- |
| 결제 슬롯 만료 | Scheduler | 매 1분 | `payments.status`, `slot_expires_at` 조건부 갱신 |
| 모집 종료 판정 | Scheduler | 매 5분 | `group_buys.status=RECRUITING` 조건부 갱신 |
| 디지털 자산 컨펌 조회 | Polling / Chain Scan | 기본 5초 | `(tx_hash, log_index)` UNIQUE |
| 배송 기한 감지 | Scheduler | 매 1시간 | 위반 후보 존재 여부 확인 |
| 자동 구매확정 | Scheduler | 매 1시간 | 참여 상태 조건부 갱신 |
| 반품 자동 승인 | Scheduler | 매 1시간 | `REQUESTED`와 처리기한 조건부 갱신 |
| 반품 자동 환불 | Scheduler | 매 1시간 | `APPROVED`와 `auto_refund_due_at` 조건부 갱신 |
| 환불 재처리 | Scheduler / Admin API | 정책 주기 | refund ID와 기관거래번호 |
| 판매자 정산 | Scheduler / Admin API | 매일 03:00 | settlement ID와 기관거래번호 |
| 카드 청구서 발행 반영 | Scheduler | 일 1회 | 결제 후 8일 경과 건 조건부 갱신 |

외부 호출은 DB 트랜잭션 내부에서 장시간 대기하지 않는다. 호출 전 `PROCESSING` 상태와 요청 식별자를 커밋하고, 외부 응답을 받은 뒤 별도 트랜잭션에서 결과를 반영한다.

## 19. 최신 ERDCloud 적용 원칙

- 계정은 `users`로 통합하고 판매자 전환 정보는 `seller_profiles`, 인증 이력은 `seller_verifications`에서 관리한다.
- 사용자 배송지는 `user_addresses`에 저장하며 결제·참여의 구매 주체 FK는 모두 `user_id`를 사용한다.

- 결제 진행 슬롯은 `payments(PROCESSING)`로 영속화하고 별도 슬롯 API와 테이블을 만들지 않는다.
- 카드·카카오페이는 5분, 디지털 자산은 10분의 `slot_expires_at`을 사용한다.
- 결제 실패 시 슬롯을 즉시 해제하며, 재시도는 새로운 `payments` 행과 새로운 멱등키로 시작한다.
- AI 분석은 동기 호출로 처리하며 분석 중간 결과를 메인 DB에 저장하지 않는다.
- 수동 구매확정을 하더라도 판매자 정산은 `delivered_at + 7일`인 `settlement_eligible_at` 이전에 실행하지 않는다.
- 구매확정 후라도 청약철회 기간 안의 반품은 `post_confirm=true`, `admin_review_required=true`로 접수한다.
- `settlement_offsets`, `receivables`와 이에 대응하는 API는 만들지 않는다.
- 도서산간 추가금은 ERD v2 저장 구조에 없으므로 MVP에서 제외한다.

## 20. 구현 전 환경값 확인 항목

- 플랫폼 수수료는 배송비를 제외한 확정 상품 분담금 합계의 5%로 적용한다.
- 정산 가능 시점은 수동 구매확정 여부와 무관하게 배송완료 후 7일로 적용한다.
- 카카오페이 성공·취소·실패 Redirect URL과 프론트엔드 최종 경로
- 카드 취소 불가 판정 기준과 계좌이체 환불 전환 조건
- 디지털 자산 MVP 지원 자산과 컨트랙트 주소
- 상품 이미지 저장소를 EC2 로컬로 할지 S3 호환 저장소로 할지
