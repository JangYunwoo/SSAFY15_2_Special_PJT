import { api } from '@/shared/api/client'
import { authTokensSchema, sellerProfileSchema, userProfileSchema } from '@/shared/api/schemas'

import type {
  AuthTokens,
  LoginRequest,
  SellerAccountRequestResult,
  SellerPhoneRequestResult,
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
    api.post<AuthTokens>('/auth/login', body, { schema: authTokensSchema }),

  /**
   * AUTH-03 액세스 토큰 재발급.
   *
   * SecurityConfig 는 이 경로를 열어두었지만 컨트롤러가 아직 없습니다.
   * 실패하면 sessionStore 가 조용히 세션을 정리하고, 컨트롤러가 생기는 즉시
   * 그대로 동작합니다.
   */
  refresh: (refreshToken: string) =>
    api.post<AuthTokens>('/auth/refresh', { refreshToken }, { skipRefresh: true }),

  /** USR-01 */
  me: () => api.get<UserProfile>('/users/me', { schema: userProfileSchema }),

  /** USR-02 */
  updateMe: (body: Partial<UserProfileUpdate>) => api.patch<UserProfile>('/users/me', body),

  /** SEL-01 판매자 전환 휴대폰 인증 요청(목업). */
  requestSellerPhone: (phone: string) =>
    api.post<SellerPhoneRequestResult>('/users/me/seller-verifications/phone/request', { phone }),

  /** SEL-02 휴대폰 인증 확인. */
  confirmSellerPhone: (phone: string, code: string) =>
    api.post<void>('/users/me/seller-verifications/phone/confirm', { phone, code }),

  /** SEL-03 정산계좌 1원 인증 요청(실제 SSAFY, 계좌 없으면 자동 개설). */
  requestSellerAccount: () =>
    api.post<SellerAccountRequestResult>('/users/me/seller-verifications/account/request'),

  /** SEL-04 정산계좌 1원 인증 확인(실제 SSAFY). */
  confirmSellerAccount: (accountNo: string, authCode: string) =>
    api.post<void>('/users/me/seller-verifications/account/confirm', { accountNo, authCode }),

  /** SEL-05 판매자 전환 확정. 성공 시 isSeller=true 인 새 토큰을 돌려줍니다. */
  createSellerProfile: (body: SellerProfileCreateRequest) =>
    api.post<AuthTokens>('/users/me/seller-profile', body),

  /** SEL-06 판매자 프로필. 판매자로 전환된 계정만 호출할 수 있습니다 (그 외 403). */
  sellerMe: () => api.get<SellerProfile>('/sellers/me', { schema: sellerProfileSchema }),

  /** SEL-07 */
  updateSellerMe: (body: Partial<SellerProfileUpdate>) =>
    api.patch<SellerProfile>('/sellers/me', body),
} as const
