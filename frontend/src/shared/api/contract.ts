import type { ZodType } from 'zod'

/**
 * 서버 응답이 프론트가 기대하는 모양인지 개발 중에만 확인합니다.
 *
 * 타입스크립트는 `api.get<CryptoStatus>(...)` 의 타입 인자를 믿을 뿐이라,
 * 서버가 다른 모양을 보내도 컴파일은 통과하고 화면만 조용히 빕니다.
 * 실제로 이런 불일치를 여러 번 겪었습니다.
 *
 *   - 공동구매 id 가 문자열이 아니라 JSON number 로 왔다
 *   - 코인 결제 상태의 키가 state 가 아니라 phase 였다
 *   - 결제 이력이 전용 DTO 없이 PaymentResponse 를 그대로 돌려줬다
 *   - 반품 사유가 네 가지가 아니라 두 가지였다
 *
 * 화면을 막지는 않습니다. 잘못된 값이라도 그대로 흘려보내고 콘솔에만
 * 알립니다. 개발 중 눈에 띄는 것이 목적이지, 동작을 멈추는 것이 목적이
 * 아니기 때문입니다.
 *
 * 프로덕션 번들에서는 import.meta.env.DEV 분기가 죽은 코드로 제거됩니다.
 */
export function checkContract(schema: ZodType | undefined, data: unknown, where: string): void {
  if (!import.meta.env.DEV || schema === undefined) return

  const result = schema.safeParse(data)
  if (result.success) return

  const lines = result.error.issues.map((issue) => {
    const path = issue.path.length === 0 ? '(최상위)' : issue.path.join('.')
    return `  ${path}: ${issue.message}`
  })

  console.error(
    [
      `[API 계약 불일치] ${where}`,
      ...lines,
      `  서버가 보낸 키: ${keysOf(data)}`,
      '  프론트 타입과 서버 응답이 다릅니다. 어느 쪽을 고칠지 정해야 합니다.',
    ].join('\n'),
  )
}

/** 어떤 키가 실제로 왔는지 보여주면 원인을 훨씬 빨리 찾습니다. */
function keysOf(data: unknown): string {
  if (Array.isArray(data)) {
    return data.length === 0 ? '(빈 배열)' : `[0] → ${keysOf(data[0])}`
  }
  if (typeof data !== 'object' || data === null) return String(data)
  return Object.keys(data).join(', ')
}
