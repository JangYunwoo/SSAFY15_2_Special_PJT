import type { ApiErrorBody, FieldError } from './types'

/** 명세서 16장의 주요 오류 코드 */
export const API_ERROR_CODES = [
  'VALIDATION_ERROR',
  'EMAIL_ALREADY_EXISTS',
  'SSAFY_EMAIL_UNAVAILABLE',
  'AUTHENTICATION_REQUIRED',
  'ROLE_ACCESS_DENIED',
  'RESOURCE_ACCESS_DENIED',
  'SELLER_SUSPENDED',
  'RESOURCE_NOT_FOUND',
  'GROUP_BUY_NOT_RECRUITING',
  'GROUP_BUY_FULL',
  'DUPLICATE_PARTICIPATION',
  'PAYMENT_ALREADY_PROCESSED',
  'PAYMENT_SLOT_EXPIRED',
  'INVALID_STATE_TRANSITION',
  'PURCHASE_CONFIRM_RETURN_CONFLICT',
  'RETURN_NOT_ELIGIBLE',
  'QUOTE_EXPIRED',
  'CRYPTO_AMOUNT_MISMATCH',
  'EXTERNAL_PAYMENT_ERROR',
  'EXTERNAL_SERVICE_UNAVAILABLE',
] as const

export type ApiErrorCode = (typeof API_ERROR_CODES)[number]

/** 화면에 그대로 노출해도 되는 안내 문구 */
const FALLBACK_MESSAGE: Record<string, string> = {
  EMAIL_ALREADY_EXISTS: '이미 가입된 이메일입니다.',
  SSAFY_EMAIL_UNAVAILABLE:
    '이 이메일은 금융망에 이미 등록되어 사용할 수 없습니다. 다른 이메일을 사용해주세요.',
  AUTHENTICATION_REQUIRED: '로그인이 필요합니다. 다시 로그인해 주세요.',
  ROLE_ACCESS_DENIED: '접근 권한이 없는 메뉴입니다.',
  RESOURCE_ACCESS_DENIED: '다른 사용자의 자원에는 접근할 수 없습니다.',
  SELLER_SUSPENDED: '판매 정지 상태에서는 등록·생성할 수 없습니다.',
  RESOURCE_NOT_FOUND: '요청한 정보를 찾을 수 없습니다.',
  GROUP_BUY_NOT_RECRUITING: '모집이 종료된 공동구매입니다.',
  GROUP_BUY_FULL: '목표 인원이 모두 채워졌습니다.',
  DUPLICATE_PARTICIPATION: '이미 참여한 공동구매입니다.',
  PAYMENT_SLOT_EXPIRED: '결제 가능 시간이 만료되었습니다. 다시 시도해 주세요.',
  PURCHASE_CONFIRM_RETURN_CONFLICT: '구매확정과 반품을 동시에 처리할 수 없습니다.',
  RETURN_NOT_ELIGIBLE: '반품 가능 기간이 지났거나 제한 사유에 해당합니다.',
  QUOTE_EXPIRED: '환율 정보가 만료되었습니다. 다시 발급해 주세요.',
  EXTERNAL_PAYMENT_ERROR: '결제기관 오류로 결제를 완료하지 못했습니다.',
  EXTERNAL_SERVICE_UNAVAILABLE: '외부 서비스가 일시적으로 응답하지 않습니다.',
}

const NETWORK_MESSAGE = '네트워크에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'

/** 서버가 명세서 형식으로 돌려준 오류 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  readonly traceId: string | undefined

  constructor(status: number, body: ApiErrorBody, traceId?: string) {
    // 서버 문구를 우선 쓰고, 비어 있으면 코드별 기본 안내로 대체합니다.
    super(
      body.message === ''
        ? (FALLBACK_MESSAGE[body.code] ?? '요청을 처리할 수 없습니다.')
        : body.message,
    )
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.fieldErrors = body.fieldErrors
    this.traceId = traceId
  }

  /** 특정 필드의 첫 검증 오류 메시지 */
  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((error) => error.field === field)?.reason
  }

  get isUnauthorized(): boolean {
    return this.status === 401
  }

  /** 사용자가 같은 요청을 반복해도 결과가 같은, 재시도가 의미 없는 오류 */
  get isTerminal(): boolean {
    return this.status >= 400 && this.status < 500 && this.status !== 408 && this.status !== 429
  }
}

/** 요청 자체가 나가지 못했거나 응답을 해석할 수 없는 경우 */
export class NetworkError extends Error {
  override readonly cause: unknown

  constructor(cause: unknown) {
    super(NETWORK_MESSAGE)
    this.name = 'NetworkError'
    this.cause = cause
  }
}

/** 어떤 오류든 화면에 띄울 한 줄 문구로 변환합니다. */
export function toMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof NetworkError) return NETWORK_MESSAGE
  if (error instanceof Error && error.message) return error.message
  return '알 수 없는 오류가 발생했습니다.'
}

export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code)
}
