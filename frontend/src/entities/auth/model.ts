import type { Id, IsoDateTime, Role } from '@/shared/api/types'

/**
 * 로그인 응답의 actor (명세서 2장).
 *
 * 계정은 `users` 하나로 통합돼 있고 구매자·판매자가 별개 계정이 아닙니다.
 * 판매자센터 이용 자격은 `isSeller` 로만 판단합니다 (SEL-05 전환 시 true).
 */
export interface Actor {
  id: Id
  role: Role
  isSeller: boolean
  name: string
}

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  /** 초 단위 만료 시간 */
  expiresIn: number
  actor: Actor
}

export interface LoginRequest {
  email: string
  password: string
}

export interface AddressInput {
  recipientName: string
  recipientPhone: string
  zipcode: string
  address1: string
  address2: string
}

/** AUTH-01 통합 회원가입 */
export interface SignupRequest extends LoginRequest {
  nickname: string
  phone: string
  defaultAddress: AddressInput
}

/**
 * AUTH-01 응답 (백엔드 SignupResponse).
 * 계정만 만들고 토큰은 주지 않으므로, 가입 후에는 따로 로그인해야 합니다.
 */
export interface SignupResult {
  id: Id
  email: string
  nickname: string
}

/**
 * SEL-05 판매자 전환 요청.
 *
 * 휴대폰(SEL-01/02)과 정산계좌(SEL-03/04) 인증을 마친 계정만 전환할 수 있고,
 * 전환 후에도 같은 user_id 를 seller_id 로 씁니다.
 */
export interface SellerProfileRequest {
  businessName: string
  businessPhone: string
  ssafyAccountNo: string
  accountHolder: string
  returnAddress: Omit<AddressInput, 'recipientName' | 'recipientPhone'>
}

export interface SettlementAccount {
  bankCode: string
  accountNumber: string
  holderName: string
}

/** USR-01 사용자 프로필 */
export interface UserProfile {
  id: Id
  email: string
  nickname: string
  phone: string
  isSeller: boolean
  createdAt: IsoDateTime
}

/** SEL-06 판매자 프로필 */
export interface SellerProfile {
  id: Id
  email: string
  shopName: string
  phone: string
  settlementAccount: SettlementAccount
  returnAddress: AddressInput
  /** 확정 위반 누적 횟수. 3회면 신규 등록·생성이 막힙니다 (명세서 13장) */
  penaltyCount: number
  suspended: boolean
  suspendedUntil: IsoDateTime | null
}
