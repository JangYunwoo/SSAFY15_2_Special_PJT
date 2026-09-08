/** 조건부 클래스 이름을 합칩니다. */
export function cx(...values: (string | false | null | undefined)[]): string {
  return values.filter(Boolean).join(' ')
}
