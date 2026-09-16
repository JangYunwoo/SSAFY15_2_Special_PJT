import { api } from '@/shared/api/client'
import type { NoContent } from '@/shared/api/types'

import type {
  AuthTokens,
  LoginRequest,
  SellerAccountRequestResult,
  SellerProfile,
  SellerProfileCreateRequest,
  SellerProfileUpdate,
  SignupRequest,
  SignupResult,
  UserProfile,
  UserProfileUpdate,
} from './model'

/**
 * 계정·인증 API.
 *
 * 명세서의 SEL-01·02(휴대폰 인증), SEL-05(판매자 전환) 는 백엔드에 컨트롤러가
 * 없어 빼두었습니다. `SellerProfileController` 는 조회·수정만 있고 생성 API 가
 * 없으므로, 판매자 계정은 지금은 DB 시드로만 만들 수 있습니다.
 * (`shared/config/backendFeatures.ts` 의 sellerOnboarding 참고)
 */
export const authApi = {
  /** AUTH-01 통합 회원가입 */
  signup: (body: SignupRequest) => api.post<SignupResult>('/auth/signup', body),
  /**
   * 이메일 중복확인·예약. 가입 전 중복확인 버튼용(C안).
   *
   * DB 중복뿐 아니라 SSAFY 금융망 선점까지 가입 전에 판별한다. 확인이 SSAFY 회원
   * 생성(쓰기)을 유발할 수 있어 조회가 아닌 POST 다. status: AVAILABLE / TAKEN /
   * UNAVAILABLE_SSAFY / UNKNOWN. (available 은 "이 상태로 진행해도 되는지")
   */
  checkEmailAvailability: (email: string) =>
    api.post<{ available: boolean; status: string }>('/auth/email/availability', { email }),

  /** AUTH-02 통합 로그인 */
  login: (body: LoginRequest) =>
    api.post<AuthTokens>('/auth/login', body, { schema: 'authTokens' }),

  /**
   * AUTH-03 액세스 토큰 재발급 (2026-09-15 백엔드 연동 확인).
   *
   * 백엔드는 리프레시 토큰을 돌려 씁니다. 재발급에 성공하면 새 리프레시 토큰이
   * 함께 오고 쓰던 토큰은 폐기되므로, 응답을 통째로 저장해야 다음 재발급이
   * 됩니다(sessionStore 의 signIn). 같은 토큰으로 두 번 부르면 거절당합니다.
   *
   * skipRefresh 로 보내는 이유는 이 요청이 401 일 때 다시 재발급을 시도하면
   * 무한히 되돌기 때문입니다.
   */
  refresh: (refreshToken: string) =>
    api.post<AuthTokens>('/auth/refresh', { refreshToken }, { skipRefresh: true }),

  /**
   * AUTH-04 로그아웃. 서버에서 리프레시 토큰을 폐기합니다.
   *
   * 이 호출이 없으면 브라우저에서 토큰만 버리는 셈이라, 서버는 그 리프레시
   * 토큰을 계속 유효하게 봅니다. 토큰이 남아 있는 쪽에서는 로그아웃 뒤에도
   * 액세스 토큰을 새로 받아 쓸 수 있습니다.
   *
   * skipRefresh 인 이유는 재발급과 같습니다. 세션을 끝내는 요청이 401 을
   * 받았다고 토큰을 되살리려 들면 앞뒤가 맞지 않습니다.
   */
  logout: (refreshToken: string) =>
    api.post<NoContent>('/auth/logout', { refreshToken }, { skipRefresh: true }),

  /** USR-01 */
  me: () => api.get<UserProfile>('/users/me', { schema: 'userProfile' }),

  /** USR-02 */
  updateMe: (body: Partial<UserProfileUpdate>) => api.patch<UserProfile>('/users/me', body),

  /** SEL-03 정산계좌 1원 인증 요청(실제 SSAFY, 계좌 없으면 자동 개설). */
  requestSellerAccount: () =>
    api.post<SellerAccountRequestResult>('/users/me/seller-verifications/account/request'),

  /** SEL-04 정산계좌 1원 인증 확인(실제 SSAFY). */
  confirmSellerAccount: (accountNo: string, authCode: string) =>
    api.post<NoContent>('/users/me/seller-verifications/account/confirm', { accountNo, authCode }),

  /** SEL-05 판매자 전환 확정. 성공 시 isSeller=true 인 새 토큰을 돌려줍니다. */
  createSellerProfile: (body: SellerProfileCreateRequest) =>
    api.post<AuthTokens>('/users/me/seller-profile', body),

  /** SEL-06 판매자 프로필. 판매자로 전환된 계정만 호출할 수 있습니다 (그 외 403). */
  sellerMe: () => api.get<SellerProfile>('/sellers/me', { schema: 'sellerProfile' }),

  /** SEL-07 */
  updateSellerMe: (body: Partial<SellerProfileUpdate>) =>
    api.patch<SellerProfile>('/sellers/me', body),
} as const
