/**
 * 카드 입력값과 그 표시 형식.
 *
 * 이 값은 CARD-01 요청에만 쓰고 스토어·localStorage·로그에 남기지 않습니다
 * (명세서 6장). 그래서 전역 상태가 아니라 결제 화면의 지역 state 로만 들고
 * 있다가 승인 후 즉시 비웁니다.
 */
export interface CardInput {
  cardNumber: string
  expiry: string
  cvc: string
}

export const EMPTY_CARD: CardInput = { cardNumber: '', expiry: '', cvc: '' }

/** "1234123412341234" → "1234 1234 1234 1234" */
export function formatCardNumber(raw: string): string {
  return (
    raw
      .replace(/\D/g, '')
      .slice(0, 16)
      .match(/.{1,4}/g)
      ?.join(' ') ?? ''
  )
}

/** "1226" → "12 / 26" */
export function formatExpiry(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 4)
  return digits.length <= 2 ? digits : `${digits.slice(0, 2)} / ${digits.slice(2)}`
}

/** 카드번호 16자리와 CVC 3자리 이상이 채워졌는지 */
export function isCardComplete(value: CardInput): boolean {
  return value.cardNumber.replace(/\D/g, '').length === 16 && value.cvc.length >= 3
}
