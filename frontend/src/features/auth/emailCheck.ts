import { isApiError } from '@/shared/api/errors'

/**
 * 이메일 중복확인의 순수 로직.
 *
 * 화면·네트워크와 떼어놓아 단위 테스트로 덮습니다. 상태 전이와 문구가 여기
 * 모여 있으므로, UX 문구를 바꿀 때 컴포넌트를 뒤질 필요가 없습니다.
 */

/**
 * 백엔드 `SignupRequest.email` 의 `@Email` 과 같은 수준으로만 봅니다.
 * 프론트에서 과하게 조이면 서버는 받는 주소를 눌러버리게 됩니다.
 */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export type EmailCheckStatus =
  /** 아직 확인하지 않음 (또는 확인 후 이메일을 고쳐서 결과가 무효가 됨) */
  | 'idle'
  | 'checking'
  /** 형식이 어긋나 요청을 보내지 않음 */
  | 'invalid'
  | 'available'
  | 'duplicate'
  /** SSAFY 금융망을 다른 곳이 선점해 이 이메일로는 연동이 불가 (가입 불가) */
  | 'unavailable'
  /** SSAFY 장애 등으로 이번엔 확인하지 못함 (가입은 진행 가능) */
  | 'unknown'
  /** 백엔드에 확인 API 가 아직 없음 (BACKEND_FEATURES.emailAvailability) */
  | 'unsupported'
  /** 네트워크·서버 오류로 확인하지 못함 */
  | 'failed'

export interface EmailCheckOutcome {
  status: EmailCheckStatus
  message: string
  /** 이 상태로 가입을 진행해도 되는지 */
  confirmed: boolean
}

/** 확인을 마친 이메일과 그 결과. 입력이 바뀌면 버려집니다. */
export interface EmailCheckRecord {
  /** normalizeEmail 을 거친 값 */
  email: string
  outcome: EmailCheckOutcome
}

const outcome = (
  status: EmailCheckStatus,
  message: string,
  confirmed = false,
): EmailCheckOutcome => ({
  status,
  message,
  confirmed,
})

export const IDLE = outcome('idle', '')
export const CHECKING = outcome('checking', '확인하는 중입니다…')
export const INVALID = outcome('invalid', '이메일 형식이 올바르지 않습니다.')
export const EMPTY = outcome('invalid', '이메일을 입력한 뒤 눌러주세요.')
export const AVAILABLE = outcome('available', '사용할 수 있는 이메일입니다.', true)
export const DUPLICATE = outcome('duplicate', '이미 가입된 이메일입니다.')

/**
 * SSAFY 금융망 선점. 다른 apiKey 가 이미 가져간 이메일이라 우리로는 연동이 영영
 * 불가능해 가입해도 결제가 막힌다. 그래서 가입 전에 잡아 진행을 막는다.
 */
export const UNAVAILABLE = outcome(
  'unavailable',
  '이 이메일은 금융망에 이미 등록되어 사용할 수 없습니다. 다른 이메일을 사용해주세요.',
)

/**
 * 금융망 장애 등으로 확인하지 못한 경우. 막지 않는다 - 가입 시점에 서버가 다시
 * 확인하며, 여기서 붙잡으면 장애 동안 아무도 가입하지 못하게 된다.
 */
export const UNKNOWN = outcome(
  'unknown',
  '지금은 사용 가능 여부를 확인하지 못했습니다. 가입은 계속 진행할 수 있어요.',
  true,
)

/** 서버 응답. `available` 은 "이 상태로 진행해도 되는지", `status` 가 구체 사유. */
export interface EmailAvailabilityResult {
  available: boolean
  status: string
}

/** 서버 판정({@link EmailAvailabilityResult})을 화면용 결과로 옮긴다. */
export function outcomeForResult(result: EmailAvailabilityResult): EmailCheckOutcome {
  switch (result.status) {
    case 'AVAILABLE':
      return AVAILABLE
    case 'TAKEN':
      return DUPLICATE
    case 'UNAVAILABLE_SSAFY':
      return UNAVAILABLE
    case 'UNKNOWN':
      return UNKNOWN
    // 모르는 status 는 available 플래그로 안전하게 갈라 처리한다.
    default:
      return result.available ? AVAILABLE : DUPLICATE
  }
}

/**
 * 확인 API 가 없을 때. 가입을 막지 않습니다 - 서버가 가입 시점에
 * `EMAIL_ALREADY_EXISTS` 로 걸러주므로, 여기서 붙잡아 두면 가입 자체가 불가능해집니다.
 */
export const UNSUPPORTED = outcome(
  'unsupported',
  '중복확인은 준비 중입니다. 가입 단계에서 자동으로 확인됩니다.',
  true,
)

export const failed = (message: string): EmailCheckOutcome => outcome('failed', message)

/** 가입 버튼 앞에서 막혔을 때 이메일 칸에 띄울 문구 */
export const NEEDS_CHECK = '이메일 중복확인을 먼저 진행해 주세요.'

/**
 * 비교용 정규화.
 *
 * `users.email` 컬럼이 utf8mb4_unicode_ci(대소문자 구분 없음)라 서버는
 * `A@x.com` 과 `a@x.com` 을 같은 계정으로 봅니다. 확인 결과를 이메일에
 * 묶어둘 때도 같은 기준을 써야 대문자만 고쳤을 때 결과가 헛돌지 않습니다.
 */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase()
}

/** 형식 문제가 있으면 그 결과를, 보낼 수 있으면 null 을 돌려줍니다. */
export function validateEmail(email: string): EmailCheckOutcome | null {
  const trimmed = email.trim()
  if (trimmed === '') return EMPTY
  if (!EMAIL_PATTERN.test(trimmed)) return INVALID
  return null
}

/**
 * 지금 입력값에 대한 확인 결과.
 *
 * 확인을 마친 뒤 이메일을 고치면 결과를 버립니다. 그러지 않으면 `a@x.com` 으로
 * 통과시킨 확인이 `b@x.com` 가입에 그대로 쓰입니다.
 */
export function outcomeFor(record: EmailCheckRecord | null, email: string): EmailCheckOutcome {
  if (record === null) return IDLE
  return record.email === normalizeEmail(email) ? record.outcome : IDLE
}

/**
 * "이 이메일로는 가입할 수 없다"는 뜻의 서버 오류 코드.
 *
 * - `EMAIL_ALREADY_EXISTS` - 우리 DB에 이미 있는 이메일
 * - `SSAFY_EMAIL_UNAVAILABLE` - SSAFY 금융망을 다른 apiKey가 선점해 연동이
 *   영영 불가능한 이메일. 계정을 만들어봐야 결제가 막히므로 가입 단계에서 거절된다.
 *
 * 원인이 모두 이메일 한 칸이므로 폼 하단 오류 상자가 아니라 그 칸에 붙인다.
 */
const EMAIL_REJECTION_CODES = ['EMAIL_ALREADY_EXISTS', 'SSAFY_EMAIL_UNAVAILABLE']

export function isEmailRejection(error: unknown): boolean {
  return EMAIL_REJECTION_CODES.some((code) => isApiError(error, code))
}

/**
 * 이 상태에서 가입을 막을지.
 *
 * 확인 API 가 없으면(`checkEnabled=false`) 막지 않습니다. 눌러도 확인되는 게
 * 없는 버튼을 통과 의례로 강요하는 꼴이 되고, 중복은 어차피 서버가 가입
 * 시점에 `EMAIL_ALREADY_EXISTS` 로 걸러냅니다.
 */
export function blocksSignup(outcome: EmailCheckOutcome, checkEnabled: boolean): boolean {
  return checkEnabled && !outcome.confirmed
}

/** 오류 문구로 보여줄 상태인지 (아니면 안내 문구) */
export function isErrorOutcome({ status }: EmailCheckOutcome): boolean {
  return (
    status === 'invalid' ||
    status === 'duplicate' ||
    status === 'unavailable' ||
    status === 'failed'
  )
}
