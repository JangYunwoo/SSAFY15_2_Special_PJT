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

백엔드가 아직 없어도 **MSW 목 서버**가 명세서 형식으로 응답하므로 모든 화면이 동작합니다.

| 스크립트 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 (MSW 목 서버 포함) |
| `npm run build` | 타입 검사 + 프로덕션 빌드 |
| `npm run typecheck` | 타입 검사만 |
| `npm run lint` | ESLint (`lint:fix` 로 자동 수정) |
| `npm run format` | Prettier 포매팅 |

### 목 서버 끄고 실제 백엔드에 붙이기

`.env.development` 에서:

```env
VITE_API_MOCKING=false
VITE_API_PROXY_TARGET=http://localhost:8080
```

`/api` 요청은 `vite.config.ts` 의 프록시가 백엔드로 넘깁니다. 목 서버는 프로덕션
번들에서 자동으로 제외됩니다(`src/main.tsx` 의 `import.meta.env.DEV` 분기).

## 기술 스택

| 영역 | 선택 | 이유 |
| --- | --- | --- |
| 빌드 | Vite 8 | 라우트 단위 코드 스플리팅 |
| 언어 | TypeScript 6 (strict + `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`) | 명세서의 상태 전이·금액 계산을 컴파일 타임에 고정 |
| 라우팅 | React Router 8 (declarative) | 역할별 가드와 레이아웃 중첩 |
| 서버 상태 | TanStack Query 5 | 캐시·무효화·로딩/오류 상태를 한곳에서 |
| 클라이언트 상태 | Zustand 5 | 세션과 결제 단계 값만 (그 외는 서버 상태) |
| 목 서버 | MSW 2 | 백엔드 완성 전 병렬 개발 |
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
│   ├── product/            # 이미지 업로더 · AI 추출
│   └── seller/             # 약관 동의 · 본인인증 코드
│
├── entities/               # 도메인별 타입 · API · 쿼리 정의
│   ├── model.ts            # 공통 Enum + 상태 표기 매핑 (명세서 17장)
│   └── auth/ address/ category/ product/ groupBuy/ payment/
│       participation/ delivery/ returnRequest/ settlement/ notification/
│       각 폴더: model.ts (타입) · api.ts (엔드포인트) · queries.ts (queryOptions)
│
├── shared/                 # 도메인을 모르는 공용 코드
│   ├── api/                # client · types · errors · queryKeys
│   ├── config/             # env · policy (정책값 + 금액 계산)
│   ├── lib/                # format · cx · dom · hooks
│   └── ui/                 # Button · Panel · DataRow · Field · StateChip …
│
├── mocks/                  # MSW (백엔드 완성 후 폴더째 삭제)
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

## 백엔드 연동 시 확인할 것

명세서 20장의 미결 항목이 프론트에도 그대로 남아 있습니다.

- **상품 이미지 업로드 엔드포인트** — 현재 `SellerProductNewPage` 는 파일을 로컬
  미리보기만 하고 `imageUrls: []` 로 등록합니다.
- **카카오페이 Redirect URL** — `PaymentPage` 는 `nextRedirectPcUrl` 로 이동만
  하고, 돌아왔을 때 KAKA-02(approve)를 호출하는 콜백 라우트가 아직 없습니다.
- **판매자 본인인증 기관 연동** — `SellerVerifyPage` 의 인증번호 검증은 아직
  서버로 보내지 않고, 세션의 `sellerVerified` 플래그만 세웁니다.
- **토큰 저장 방식** — 지금은 `localStorage`(persist)입니다. Refresh 토큰을
  HttpOnly 쿠키로 내리기로 정하면 `sessionStore.ts` 의 `partialize` 에서 토큰을
  제외하세요.
- **디지털 자산 결제** — CRYP-03(트랜잭션 해시 등록) 화면이 지갑 주소까지만
  받습니다. 입금 확인·컨펌 폴링 UI는 `paymentQueries.cryptoStatus` 를 붙여
  만들면 됩니다.
