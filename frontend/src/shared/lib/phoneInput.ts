/** 국내 전화번호 입력. 상태·API에는 숫자만, 화면에는 하이픈을 사용한다. */
export function phoneDigits(value: string): string {
  return value.replace(/\D/g, '').slice(0, 11)
}

export function formatPhoneInput(value: string): string {
  const digits = phoneDigits(value)
  const prefix = digits.startsWith('02') ? 2 : 3
  if (digits.length <= prefix) return digits
  if (digits.length <= prefix + 4) return `${digits.slice(0, prefix)}-${digits.slice(prefix)}`
  const middle = digits.length - prefix > 7 ? 4 : 3
  return `${digits.slice(0, prefix)}-${digits.slice(prefix, prefix + middle)}-${digits.slice(prefix + middle)}`
}

/** 서식이 바뀌어도 커서 앞의 숫자 개수를 유지한다. */
export function phoneCaret(formatted: string, digitCount: number): number {
  if (digitCount <= 0) return 0
  let seen = 0
  for (let i = 0; i < formatted.length; i++) {
    if (/\d/.test(formatted[i] ?? '') && ++seen === digitCount) return i + 1
  }
  return formatted.length
}
