/**
 * 식별자 비교.
 *
 * 명세서 1.5 는 식별자를 문자열로 내리도록 권장하고 상품·결제·에스크로 DTO 는
 * 그렇게 하지만, 공동구매 응답은 아직 JSON number 로 옵니다(S15P21B205-121).
 * URL 파라미터는 언제나 문자열이라 그냥 === 로 비교하면 늘 거짓이 됩니다.
 *
 * 백엔드가 문자열로 맞추면 이 함수는 지워도 됩니다.
 */
export function sameId(a: string | number, b: string | number): boolean {
  return String(a) === String(b)
}
