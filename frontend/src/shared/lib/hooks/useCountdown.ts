import { useNow } from './useNow'

interface Countdown {
  secondsLeft: number
  isExpired: boolean
}

/**
 * 만료 시각까지 남은 초.
 * 결제 슬롯(PAY-01), 코인 Quote(CRYP-02), 본인인증 코드에서 씁니다.
 *
 * 남은 초를 state 에 담아 1씩 줄이는 대신 매 tick 마다 만료 시각과 현재 시각의
 * 차이를 다시 구합니다. 탭이 백그라운드로 가서 타이머가 밀려도 복귀 시
 * 실제 남은 시간이 표시됩니다.
 */
export function useCountdown(expiresAt: string | null | undefined): Countdown {
  const now = useNow()

  if (expiresAt === null || expiresAt === undefined) {
    return { secondsLeft: 0, isExpired: false }
  }

  const secondsLeft = Math.max(0, Math.ceil((new Date(expiresAt).getTime() - now) / 1000))
  return { secondsLeft, isExpired: secondsLeft <= 0 }
}
