import type { IsoDateTime, Krw } from '@/shared/api/types'

const krwFormatter = new Intl.NumberFormat('ko-KR')
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  timeZone: 'Asia/Seoul',
})
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Asia/Seoul',
})

/** 1040000 → "1,040,000원" */
export function won(amount: Krw): string {
  return `${krwFormatter.format(amount)}원`
}

/** 부호를 붙여 표시 (정산 차감 등). -10000 → "− 10,000원" */
export function signedWon(amount: Krw): string {
  return amount < 0 ? `− ${won(Math.abs(amount))}` : won(amount)
}

export function number(value: number): string {
  return krwFormatter.format(value)
}

/** 0.05 → "5%" */
export function percent(rate: number, fractionDigits = 0): string {
  return `${(rate * 100).toFixed(fractionDigits)}%`
}

/** "2026-09-04T14:59:59Z" → "2026.09.04" */
export function date(value: IsoDateTime | null | undefined): string {
  if (!value) return '-'
  return dateFormatter.format(new Date(value)).replaceAll('. ', '.').replace(/\.$/, '')
}

export function dateTime(value: IsoDateTime | null | undefined): string {
  if (!value) return '-'
  return dateTimeFormatter.format(new Date(value))
}

/** 초 → "09:55" (결제 슬롯·Quote 타이머) */
export function clock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

/** 남은 시간을 "3일 12시간 남음" 형태로 표기합니다. */
export function remaining(until: IsoDateTime, now: Date = new Date()): string {
  const ms = new Date(until).getTime() - now.getTime()
  if (ms <= 0) return '마감'

  const totalHours = Math.floor(ms / 3_600_000)
  if (totalHours < 1) return `${String(Math.ceil(ms / 60_000))}분 남음`
  if (totalHours < 24) return `${String(totalHours)}시간 남음`

  const days = Math.floor(totalHours / 24)
  const hours = totalHours % 24
  return hours ? `${String(days)}일 ${String(hours)}시간 남음` : `${String(days)}일 남음`
}

/** 기한까지 남은 일수를 "D-4" 형태로 표기합니다. */
export function dday(until: IsoDateTime | null | undefined, now: Date = new Date()): string {
  if (!until) return ''
  const days = Math.ceil((new Date(until).getTime() - now.getTime()) / 86_400_000)
  if (days < 0) return '기한 종료'
  return days === 0 ? 'D-DAY' : `D-${String(days)}`
}

/** "01012345678" → "010-1234-5678" */
export function phone(value: string): string {
  const digits = value.replace(/\D/g, '')
  if (digits.length === 11) return digits.replace(/(\d{3})(\d{4})(\d{4})/, '$1-$2-$3')
  if (digits.length === 10) return digits.replace(/(\d{3})(\d{3})(\d{4})/, '$1-$2-$3')
  return value
}

/** 이*연 처럼 가운데를 가립니다. */
export function maskName(name: string): string {
  if (name.length <= 1) return name
  if (name.length === 2) return `${name[0] ?? ''}*`
  return `${name[0] ?? ''}${'*'.repeat(name.length - 2)}${name.at(-1) ?? ''}`
}

/**
 * 공동구매 번호를 #1024 형태로 표기합니다.
 *
 * 명세서 1.5 에 따라 서버 식별자는 문자열로 오지만, 오래된 캐시나 목 응답의 숫자도
 * 화면을 깨뜨리지 않도록 String()으로 정규화한 뒤 자릿수를 채웁니다.
 */
export function groupBuyNo(id: string | number): string {
  return `#${String(id).padStart(4, '0')}`
}
