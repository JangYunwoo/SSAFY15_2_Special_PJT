import { api } from '@/shared/api/client'
import type { NoContent } from '@/shared/api/types'

import type {
  AuthTokens,
  BuyerProfile,
  BuyerSignupRequest,
  LoginRequest,
  SellerProfile,
  SellerSignupRequest,
} from './model'

/** 계정 및 인증 API (AUTH-01 ~ SEL-02) */
export const authApi = {
  /** AUTH-01 */
  signupBuyer: (body: BuyerSignupRequest) => api.post<AuthTokens>('/auth/buyers/signup', body),
  /** AUTH-02 */
  loginBuyer: (body: LoginRequest) => api.post<AuthTokens>('/auth/buyers/login', body),
  /** AUTH-03 */
  signupSeller: (body: SellerSignupRequest) => api.post<AuthTokens>('/auth/sellers/signup', body),
  /** AUTH-04 */
  loginSeller: (body: LoginRequest) => api.post<AuthTokens>('/auth/sellers/login', body),
  /** AUTH-05 */
  refresh: (refreshToken: string) => api.post<AuthTokens>('/auth/refresh', { refreshToken }),
  /** AUTH-06 */
  logout: () => api.post<NoContent>('/auth/logout'),

  /** BUY-01 */
  buyerMe: () => api.get<BuyerProfile>('/buyers/me'),
  /** BUY-02 */
  updateBuyerMe: (body: Partial<Pick<BuyerProfile, 'nickname' | 'phone'>>) =>
    api.patch<BuyerProfile>('/buyers/me', body),
  /** SEL-01 */
  sellerMe: () => api.get<SellerProfile>('/sellers/me'),
  /** SEL-02 */
  updateSellerMe: (
    body: Partial<Pick<SellerProfile, 'phone' | 'settlementAccount' | 'returnAddress'>>,
  ) => api.patch<SellerProfile>('/sellers/me', body),
} as const
