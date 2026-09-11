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

/** USR-01 사용자 프로필 (백엔드 UserProfileResponse) */
export interface UserProfile {
  id: Id
  email: string
  nickname: string
  phone: string
  isSeller: boolean
  createdAt: IsoDateTime
  /** 카드 취소가 불가능할 때 환불을 받을 SSAFY 금융망 계좌 */
  ssafyAccountNo: string | null
  /** SSAFY 금융망 사용자 연동 여부. 가입 시 실패하면 로그인할 때 재시도합니다. */
  ssafyLinked: boolean
}

/** USR-02 수정 가능한 항목 */
export interface UserProfileUpdate {
  nickname: string
  phone: string
  ssafyAccountNo: string
}

/**
 * SEL-06 판매자 프로필 (백엔드 SellerProfileResponse).
 *
 * 정산 계좌는 은행 코드 없이 SSAFY 금융망 계좌번호 하나로 관리합니다.
 * 반품 주소도 중첩 객체가 아니라 평평한 세 필드로 내려옵니다.
 */
export interface SellerProfile {
  id: Id
  businessName: string
  businessPhone: string
  ssafyAccountNo: string | null
  accountHolder: string | null
  /** 1원 송금 등으로 예금주 확인을 마쳤는지 */
  accountVerified: boolean
  returnZipcode: string | null
  returnAddress1: string | null
  returnAddress2: string | null
  /** 확정 위반 누적 횟수. 3회면 신규 등록·생성이 막힙니다 (명세서 13장) */
  penaltyCount: number
  /** ACTIVE · SUSPENDED 등 백엔드 seller_profiles.status 값 */
  status: string
}

/** SEL-07 수정 가능한 항목 */
export interface SellerProfileUpdate {
  businessPhone: string
  ssafyAccountNo: string
  accountHolder: string
  returnZipcode: string
  returnAddress1: string
  returnAddress2: string
}
