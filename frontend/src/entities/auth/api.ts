import { api } from '@/shared/api/client'
import { authTokensSchema } from '@/shared/api/schemas'
import type { NoContent } from '@/shared/api/types'

import type {
  AuthTokens,
  LoginRequest,
  SellerProfile,
  SellerProfileRequest,
  SignupRequest,
  SignupResult,
  UserProfile,
} from './model'

/** 계정·인증 API (AUTH-01 ~ AUTH-04, USR-01/02, SEL-01 ~ SEL-07) */
export const authApi = {
  /** AUTH-01 통합 회원가입 */
  signup: (body: SignupRequest) => api.post<SignupResult>('/auth/signup', body),
  /**
   * AUTH-05 이메일 중복확인. 가입 전 중복확인 버튼용.
   * available=true 면 아직 가입되지 않은 이메일이라 사용 가능.
   * (SSAFY 금융망 선점 충돌은 여기서 걸러지지 않고 가입 시점에만 판별됨)
   */
  checkEmailAvailability: (email: string) =>
    api.get<{ available: boolean }>('/auth/email/availability', { query: { email } }),
  /** AUTH-02 통합 로그인 */
  login: (body: LoginRequest) =>
    api.post<AuthTokens>('/auth/login', body, { schema: authTokensSchema }),
  /** AUTH-03 */
  refresh: (refreshToken: string) =>
    api.post<AuthTokens>('/auth/refresh', { refreshToken }, { skipRefresh: true }),
  /** AUTH-04 */
  logout: () => api.post<NoContent>('/auth/logout'),

  /** USR-01 */
  me: () => api.get<UserProfile>('/users/me'),
  /** USR-02 */
  updateMe: (body: Partial<Pick<UserProfile, 'nickname' | 'phone'>>) =>
    api.patch<UserProfile>('/users/me', body),

  /** SEL-01 판매자 전환 휴대폰 인증 요청 */
  requestSellerPhone: (phone: string) =>
    api.post<NoContent>('/users/me/seller-verifications/phone/request', { phone }),
  /** SEL-02 휴대폰 인증 확인 */
  confirmSellerPhone: (body: { phone: string; code: string }) =>
    api.post<NoContent>('/users/me/seller-verifications/phone/confirm', body),
  /** SEL-05 인증 완료 후 판매자 전환 */
  convertToSeller: (body: SellerProfileRequest) =>
    api.post<SellerProfile>('/users/me/seller-profile', body),

  /** SEL-06 */
  sellerMe: () => api.get<SellerProfile>('/sellers/me'),
  /** SEL-07 */
  updateSellerMe: (
    body: Partial<Pick<SellerProfile, 'phone' | 'settlementAccount' | 'returnAddress'>>,
  ) => api.patch<SellerProfile>('/sellers/me', body),
} as const
