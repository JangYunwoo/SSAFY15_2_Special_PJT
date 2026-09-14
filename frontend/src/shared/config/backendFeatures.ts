/**
 * 백엔드에 아직 컨트롤러가 없는 기능.
 *
 * MSW 목 서버를 걷어내고 실제 백엔드에 직접 붙였습니다. 명세서에는 있지만
 * `backend/src/main/java/com/gongguhan/domain/*` 에 구현이 없는 API 가 남아 있어,
 * 그대로 두면 화면마다 404/403 오류 상자가 뜹니다. 가짜 데이터를 만들어
 * 채우는 대신 "준비 중"으로 안내하고, 컨트롤러가 생기면 아래 값을 true 로
 * 바꾸는 것만으로 화면이 되살아나도록 한곳에 모았습니다.
 *
 * 확인 방법: `backend/**\/controller/*.java` 에 해당 경로의 매핑이 있는지 봅니다.
 * 마지막 대조: 2026-09-14 — 컨트롤러 34개의 매핑을 전부 훑었습니다.
 * 이 파일을 고친 직후 백엔드가 정산·제재·환불을 한꺼번에 병합했습니다. 날짜를
 * 적어두지 않으면 어느 시점 기준인지 알 수 없어 같은 실수를 반복하게 됩니다.
 *
 * 값의 타입을 `boolean` 으로 못 박은 이유: `as const` 로 두면 `false` 리터럴이
 * 되어 `flag && <A />` 가 "항상 거짓"으로 좁혀집니다. 그러면 린트가 죽은 코드로
 * 잡고, true 로 바꾸는 순간 타입이 달라져 다른 곳이 깨집니다. 이 값들은 컴파일
 * 상수가 아니라 연동 진행 상황을 적어둔 설정입니다.
 */
export interface BackendFeatureFlags {
  /** NOTI-01~03 · `domain/notification` 에 package-info.java 뿐 */
  notifications: boolean

  /**
   * STL-01~03 정산 조회 · `SellerSettlementController` 구현됨 (2026-09-14 병합).
   *
   * 새로 붙은 API 라 문제가 생기면 되돌릴 수 있도록 플래그를 남겨 둡니다.
   * 같은 날 들어온 PEN-01(`GET /sellers/me/penalties`)도 함께 구현됐습니다.
   */
  settlements: boolean

  /**
   * SEL-01·02·05 판매자 전환 (휴대폰 인증, 판매자 프로필 생성).
   * `SellerProfileController` 는 조회·수정(GET/PATCH /sellers/me)만 있고
   * 생성 API 가 없어 판매자 계정은 DB 시드로만 만들 수 있습니다.
   */
  sellerOnboarding: boolean

  /**
   * PAR-03 모집 완료 전 참여 취소.
   * `ParticipationQueryController` 는 조회(GET) 두 개뿐이고 취소 매핑이 없습니다.
   */
  participationCancel: boolean

  /**
   * GRP-10 판매 철회 (`POST /sellers/group-buys/{id}/withdrawal`) · 컨트롤러 없음.
   *
   * 예전 이름은 `groupBuyLifecycle` 이었고 GRP-07·08·10 이 모두 없다고 적어
   * 두었지만 사실과 달랐습니다. `SellerGroupBuyController` 에 수정(PATCH
   * /sellers/group-buys/{id})과 취소(POST /{id}/cancel)는 이미 있습니다.
   * 남은 건 철회뿐이라 이름과 범위를 거기에 맞췄습니다.
   */
  groupBuyWithdrawal: boolean

  /** AUTH-04 로그아웃 · 컨트롤러 없음 (토큰은 클라이언트에서만 폐기) */
  logout: boolean

  /**
   * 이메일 중복확인 · `GET /auth/email/availability` (`AuthController` 에 구현됨).
   *
   * 명세서에 없는 추가 API 입니다. 명세서의 AUTH-05 는 관리자 로그인
   * (`POST /auth/admins/login`) 이라 번호를 붙이지 않았습니다.
   */
  emailAvailability: boolean
}

export const BACKEND_FEATURES: BackendFeatureFlags = {
  notifications: false,
  settlements: true,
  sellerOnboarding: false,
  participationCancel: false,
  groupBuyWithdrawal: false,
  logout: false,
  emailAvailability: true,
}
