import type { StateTone } from '@/shared/ui/StateChip'

export interface Display {
  label: string
  tone: StateTone
}

/** 같은 값을 여러 번 그려도 콘솔이 같은 줄로 도배되지 않게 합니다. */
const reported = new Set<string>()

/**
 * 상태값을 화면 표시로 바꿉니다. 모르는 값이어도 화면을 죽이지 않습니다.
 *
 * 예전에는 `PAYMENT_STATUS_DISPLAY[item.status]` 처럼 표에서 바로 꺼내 썼습니다.
 * 표에 없는 값이 오면 undefined 가 되고, 이어서 `display.tone` 을 읽다가
 * TypeError 로 화면 전체가 흰 화면이 됐습니다. 목록이라면 멀쩡한 나머지
 * 항목까지 같이 사라집니다.
 *
 * 타입 검사로는 막을 수 없습니다. `Record<PaymentStatus, Display>` 는 키가 그
 * 넷뿐이라고 믿기 때문에, 런타임에 다섯 번째 값이 오는 경우를 상정하지
 * 않습니다. 백엔드가 상태를 하나 더 추가하는 일은 이 프로젝트에서 계속
 * 있었습니다(참여 취소가 들어오며 REFUNDING 이 생겼습니다).
 *
 * 그래서 모르는 값은 받은 문자열을 그대로 중립 색으로 보여줍니다. 값이 이상한
 * 것은 이상한 대로 눈에 띄어야 백엔드와 맞출 단서가 되므로, 감추거나 "알 수
 * 없음" 같은 말로 덮지 않습니다.
 */
export function displayFor<T extends string>(
  table: Record<T, Display>,
  status: string,
  where: string,
): Display {
  const known = (table as Record<string, Display | undefined>)[status]
  if (known !== undefined) return known

  if (import.meta.env.DEV) {
    const key = `${where}:${status}`
    if (!reported.has(key)) {
      reported.add(key)
      console.error(
        [
          `[상태값 불일치] ${where}`,
          `  서버가 보낸 값: ${JSON.stringify(status)}`,
          `  프론트가 아는 값: ${Object.keys(table).join(', ')}`,
          '  화면은 받은 값을 그대로 보여줍니다. 어느 쪽을 고칠지 정해야 합니다.',
        ].join('\n'),
      )
    }
  }

  // 빈 문자열이 오면 칩이 빈 칸으로 보이므로 최소한의 자리는 채웁니다.
  return { label: status === '' ? '-' : status, tone: 'neutral' }
}
