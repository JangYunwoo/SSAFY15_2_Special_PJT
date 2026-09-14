import { api } from '@/shared/api/client'
import { authTokensSchema, sellerProfileSchema, userProfileSchema } from '@/shared/api/schemas'

import type {
  AuthTokens,
  LoginRequest,
  SellerProfile,
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
   * AUTH-05 이메일 중복확인. 가입 전 중복확인 버튼용.
   * available=true 면 아직 가입되지 않은 이메일이라 사용 가능.
   * (SSAFY 금융망 선점 충돌은 여기서 걸러지지 않고 가입 시점에만 판별됨)
   */
  checkEmailAvailability: (email: string) =>
    api.get<{ available: boolean }>('/auth/email/availability', { query: { email } }),

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

  /** SEL-06 판매자 프로필. 판매자로 전환된 계정만 호출할 수 있습니다 (그 외 403). */
  sellerMe: () => api.get<SellerProfile>('/sellers/me', { schema: sellerProfileSchema }),

  /** SEL-07 */
  updateSellerMe: (body: Partial<SellerProfileUpdate>) =>
    api.patch<SellerProfile>('/sellers/me', body),
} as const
