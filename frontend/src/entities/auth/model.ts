import type { Id, IsoDateTime, Role } from '@/shared/api/types'

/** 로그인 응답의 actor (명세서 2장) */
export interface Actor {
  id: Id
  role: Role
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

export interface BuyerSignupRequest extends LoginRequest {
  nickname: string
  phone: string
  defaultAddress: AddressInput
}

export interface SellerSignupRequest extends LoginRequest {
  /** 상호 */
  shopName: string
  phone: string
  /** 사업자등록번호 (법인 판매자) */
  businessNumber?: string
  settlementAccount: SettlementAccount
  returnAddress: AddressInput
}

export interface SettlementAccount {
  bankCode: string
  accountNumber: string
  holderName: string
}

export interface BuyerProfile {
  id: Id
  email: string
  nickname: string
  phone: string
  createdAt: IsoDateTime
}

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
