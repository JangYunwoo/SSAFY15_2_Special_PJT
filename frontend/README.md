# 공구한 (Gongguhan) - Frontend

React + Vite로 구성된 공동구매 플랫폼 프론트엔드. 백엔드 API 명세는
[`../docs/api/공구한_API_명세서_v1.md`](../docs/api/공구한_API_명세서_v1.md) 참고.

```bash
npm install
npm run dev
```

## 템플릿 기본 안내 (Vite)

공동구매 플랫폼 "공구한"의 웹 프론트엔드입니다.

- 기준 문서: [`docs/공구한_API_명세서_v1.md`](../docs/공구한_API_명세서_v1.md), [`docs/공구한_schema_v2.sql`](../docs/공구한_schema_v2.sql)
- 화면 기준: `mockup/` (HTML 목업 19개 화면 + 캡처)

## 실행

```bash
npm install
cp .env.example .env.development   # 이미 있으면 생략
npm run dev                        # http://localhost:5173
```

화면에 보이는 값은 **전부 실제 백엔드 응답**입니다. 목 서버(MSW)는 제거했으므로
백엔드를 먼저 띄워야 합니다.

```bash
cd ../                 # 저장소 루트
docker compose up -d   # MySQL · Redis
cd backend && ./gradlew bootRun   # http://localhost:8080/api/v1
```

| 스크립트 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 (백엔드 프록시) |
| `npm run build` | 타입 검사 + 프로덕션 빌드 |
| `npm run typecheck` | 타입 검사만 |
| `npm run lint` | ESLint (`lint:fix` 로 자동 수정) |
| `npm run format` | Prettier 포매팅 |

### 데이터가 안 나올 때

1. 백엔드가 떠 있는지 확인합니다 — `curl http://localhost:8080/api/v1/actuator/health`
2. `.env.development` 의 `VITE_API_PROXY_TARGET` 이 백엔드 주소와 같은지 확인합니다.
3. 목록이 비어 있다면 데이터가 없는 것입니다. 공개 목록(GRP-01)은 판매자가
   공동구매를 만들어야 채워집니다.
4. 브라우저에 예전 MSW 서비스워커가 남아 요청을 가로챌 수 있습니다.
   F12 → **Application** → **Service Workers** → `mockServiceWorker.js`
   **Unregister** 후 `Ctrl+Shift+R`.

응답 모양이 프론트 타입과 다르면 콘솔에 `[API 계약 불일치]` 로그가 뜹니다
(`shared/api/contract.ts`). 화면은 막지 않고 어긋난 필드만 알려줍니다.

### 판매자 계정

판매자 전환 API(SEL-01·02·05)가 백엔드에 아직 없습니다. 판매자센터에 들어가려면
`users.is_seller = true` 이고 `seller_profiles` 행이 있는 계정으로 로그인해야 합니다
(백엔드가 JWT 의 `isSeller` 클레임으로 `ROLE_SELLER` 를 부여합니다). 세션에서
플래그만 켜는 우회는 `/sellers/**` 요청이 403 이라 의미가 없습니다.

### 아직 백엔드가 없는 기능

`shared/config/backendFeatures.ts` 에 모아두었습니다. 해당 화면은 가짜 데이터를
채우는 대신 "준비 중"으로 안내하고, 조회도 `enabled` 로 막아 불필요한 404 를
내지 않습니다. 컨트롤러가 생기면 **플래그만 true 로** 바꾸면 화면이 되살아납니다.

| 플래그 | 미구현 API | 영향받는 화면 |
| --- | --- | --- |
| `notifications` | NOTI-01~03 | 헤더 알림 벨 (숨김) |
| `settlements` | STL-01·02, PEN-01 | 정산 관리 (정책·계좌만 노출) |
| `sellerOnboarding` | SEL-01·02·05 | 판매자 전환 |
| `participationCancel` | PAR-03 | 거래관리의 참여 취소 버튼 |
| `groupBuyLifecycle` | GRP-07·08·10 | 공동구매 수정·취소·철회 |
| `logout` | AUTH-04 | 로그아웃 (클라이언트 토큰만 폐기) |

## 기술 스택

| 영역 | 선택 | 이유 |
| --- | --- | --- |
| 빌드 | Vite 8 | 라우트 단위 코드 스플리팅 |
| 언어 | TypeScript 6 (strict + `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`) | 명세서의 상태 전이·금액 계산을 컴파일 타임에 고정 |
| 라우팅 | React Router 8 (declarative) | 역할별 가드와 레이아웃 중첩 |
| 서버 상태 | TanStack Query 5 | 캐시·무효화·로딩/오류 상태를 한곳에서 |
| 클라이언트 상태 | Zustand 5 | 세션과 결제 단계 값만 (그 외는 서버 상태) |
| 스타일 | 목업 CSS 이관 (토큰 + 컴포넌트 레이어) | 디자인을 그대로 유지 |

## 폴더 구조

기능 단위로 나눈 계층형 구조입니다. **아래 계층만 import** 하는 규칙을 지키면
순환 참조가 생기지 않습니다: `app → pages → features → entities → shared`

```
src/
├── app/                    # 앱 조립: 라우터 · 레이아웃 · 프로바이더
│   ├── App.tsx
│   ├── router.tsx          # 라우트 정의 (페이지는 lazy)
│   ├── layouts/            # RootLayout · SellerLayout · CheckoutLayout · guards
│   └── providers/          # QueryProvider · ErrorBoundary
│
├── pages/                  # 라우트 1개 = 파일 1개
│   ├── HomePage.tsx                GroupBuyListPage.tsx    GroupBuyDetailPage.tsx
│   ├── AboutPage.tsx               LoginPage.tsx           SignupPage.tsx
│   ├── checkout/           # ShippingPage · PaymentPage · CompletePage
│   ├── orders/             # OrdersPage · ConfirmPage · ReturnRequestPage
│   └── seller/             # Verify · Home · Products · ProductNew · GroupBuyNew
│                           #   · Orders · Returns · Settlements
│
├── features/               # 상태를 가진 기능 단위
│   ├── auth/               # sessionStore · useAuthMutations
│   ├── groupBuy/           # 카드 · 필터 · 갤러리 · URL 쿼리 동기화
│   ├── checkout/           # 결제 슬롯 · 결제수단 · 카드/코인 입력 · 주문 요약
│   ├── orders/             # 거래 아코디언 · 타임라인 매핑
│   └── product/            # 이미지 업로더 · AI 추출
│
├── entities/               # 도메인별 타입 · API · 쿼리 정의
│   ├── model.ts            # 공통 Enum + 상태 표기 매핑 (명세서 17장)
│   └── auth/ address/ category/ product/ groupBuy/ payment/
│       participation/ delivery/ returnRequest/ settlement/ notification/
│       각 폴더: model.ts (타입) · api.ts (엔드포인트) · queries.ts (queryOptions)
│
├── shared/                 # 도메인을 모르는 공용 코드
│   ├── api/                # client · types · errors · queryKeys · schemas
│   ├── config/             # env · policy (정책값 + 금액 계산) · backendFeatures
│   ├── lib/                # format · cx · dom · hooks
│   └── ui/                 # Button · Panel · DataRow · Field · StateChip …
│
└── styles/                 # tokens.css (색) · global.css (컴포넌트)
```

## 핵심 규칙

### 1. API 응답 봉투

모든 응답은 `{ success, data, meta, traceId }` 형태입니다(명세서 1.3/1.4).
`shared/api/client.ts` 가 봉투를 벗겨 `data` 만 돌려주고, 실패는 `ApiError` 로
throw 합니다. 화면에서는 `toMessage(error)` 로 문구를 얻습니다.

```ts
// 목록은 data + meta 를 함께 받습니다
const { items, meta } = await groupBuyApi.list({ keyword: '키보드' })
```

### 2. 서버 상태는 TanStack Query, 그 외만 Zustand

- 서버에서 온 데이터 → `entities/*/queries.ts` 의 `queryOptions` + `useQuery`
- 세션(토큰·역할·판매자 인증) → `features/auth/sessionStore.ts`
- 결제 단계 사이 값(배송지·결제 ID) → `features/checkout/checkoutStore.ts`
- 목록 필터·검색어·정렬 → **URL 쿼리스트링** (`useGroupBuyListParams`)

무효화는 `shared/api/queryKeys.ts` 의 키를 씁니다.

### 3. 금액은 서버가 확정

`shared/config/policy.ts` 의 계산 함수는 **화면 미리보기 전용**입니다.
실제 청구·정산 금액은 항상 서버 응답(GRP-03 / PAY-01 / STL-02)을 신뢰하세요.

| 항목 | 값 |
| --- | --- |
| 상품 분담금 | `ceil(상품가격 ÷ 목표인원)` — 생성 시 확정, 재계산 없음 |
| 개인 결제금액 | 분담금 + 배송비(판매자 입력) |
| 플랫폼 수수료 | 분담금 합계의 **5%**, 원 단위 버림 (배송비 제외) |
| 자동 구매확정 | 배송완료 + 7일 |
| 정산 가능 시점 | 배송완료 + 7일 (수동 확정 여부와 무관) |
| 단순변심 반품비 | 공동구매 배송비의 2배 · 구매자 부담 |

### 4. 결제 슬롯

별도 슬롯 리소스는 없고 `payments.status = PROCESSING` 이 슬롯입니다.
`features/checkout/usePaymentSlot.ts` 가 결제수단을 고른 시점에 PAY-01 로 자리를
잡고, 수단을 바꾸거나 화면을 벗어나면 PAY-03 으로 반납합니다. 승인이 끝난 결제는
`markSettled()` 로 반납 대상에서 제외해야 합니다.

카드번호·CVC는 **승인 요청에만** 쓰고 전역 상태·스토리지·로그에 남기지 않습니다
(명세서 6장). 그래서 결제 화면의 지역 state 로만 들고 있다가 승인 후 비웁니다.

### 5. 목업과 달라진 부분

목업(`mockup/assets/app.js`)은 API 명세서보다 앞선 버전이라 정책이 다릅니다.
**명세서(ERD v2)를 기준**으로 구현했습니다.

| 항목 | 목업 | 적용된 값 |
| --- | --- | --- |
| 플랫폼 수수료 | 건당 정액 10,000원 | 분담금 합계의 5% 정률 |
| 배송비 | 플랫폼 고정 4,000원 | 판매자가 공동구매별로 입력 |
| 도서산간 추가금 | +4,000원 | MVP 제외 |
| 정산 상계 / 회수 채권 | 화면 있음 | 미구현 (명세서 19장에서 제외) |

## 백엔드 연동 현황

목 서버를 걷어내고 실제 백엔드(`backend/`)에 직접 붙였습니다. 엔드포인트가
실제로 있는지는 `backend/src/main/java/com/gongguhan/domain/**/controller/*.java`
의 매핑으로 확인했습니다.

### 붙어 있는 API

인증(AUTH-01·02) · 사용자·배송지(USR-01~05) · 카테고리(CAT-01) · 공동구매
공개 조회(GRP-01·02) · 판매자 공동구매 생성·목록·참여자(GRP-03·04·05·09) ·
상품·AI 추출(PRD-01~05, AI-01, WDR-01) · 결제 전체(PAY, CARD, KAKA, CRYP) ·
참여·구매확정·이의제기(PAR-01·02, CFM-01·02) · 에스크로(ESC-01·02) ·
배송(DLV-01~05) · 반품(RTN-01~09) · 판매자 프로필(SEL-06·07).

### 아직 없는 API

`shared/config/backendFeatures.ts` 의 표를 참고하세요. 해당 화면은 값을
지어내지 않고 준비 중임을 밝힙니다.

### 연동하면서 맞춘 계약

- **구매확정(CFM-01)** — 백엔드가 `noticeAgreed` 를 필수로 받습니다(FR-CFM).
  프론트가 `confirmed` 를 보내고 있어 항상 검증 실패했습니다. 확정 화면에
  고지 동의 체크박스를 두고 `noticeAgreed` 로 보냅니다.
- **판매자 프로필(SEL-06)** — 응답이 `businessName` · `ssafyAccountNo` ·
  `accountHolder` · `returnZipcode/1/2` 평면 구조입니다. 프론트가 기대하던
  `shopName` · `settlementAccount{bankCode,...}` 중첩 구조와 달라 모델을
  백엔드 DTO 에 맞췄습니다.
- **사용자 프로필(USR-01)** — 환불 수취 계좌 `ssafyAccountNo` 와 SSAFY 금융망
  연동 여부 `ssafyLinked` 가 추가로 내려옵니다.
- **판매자 자격** — 백엔드가 JWT 의 `isSeller` 클레임으로 `ROLE_SELLER` 를
  부여하므로, 세션은 서버가 준 `actor.isSeller` 만 신뢰합니다. 클라이언트에서
  플래그를 세우는 경로는 제거했습니다.
- **식별자 타입** — 명세서 1.5 는 문자열을 권하지만 공동구매·참여 응답은
  `Long`(JSON number)으로 옵니다. `shared/lib/id.ts` 의 `sameId` 로 비교하고,
  계약 스키마가 어긋난 필드를 콘솔에 알립니다.

### 남은 확인 사항

- **토큰 재발급(AUTH-03)** — `SecurityConfig` 는 `/auth/refresh` 를 열어두었지만
  컨트롤러가 없습니다. 지금은 재발급이 실패하면 세션을 정리하므로 액세스 토큰
  만료(30분) 시 다시 로그인해야 합니다. 컨트롤러가 생기면 코드 수정 없이 동작합니다.
- **토큰 저장 방식** — 지금은 `localStorage`(persist)입니다. Refresh 토큰을
  HttpOnly 쿠키로 내리기로 정하면 `sessionStore.ts` 의 `partialize` 에서 토큰을
  제외하세요.
- **디지털 자산 결제** — CRYP-03(트랜잭션 해시 등록) 화면이 지갑 주소까지만
  받습니다. 입금 확인·컨펌 폴링 UI는 `paymentQueries.cryptoStatus` 를 붙여
  만들면 됩니다.
