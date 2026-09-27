import { useNow } from './useNow'

/**
 * 시작 시각부터 흐른 초.
 *
 * {@link useCountdown} 과 같은 이유로 공용 시계를 구독합니다. 경과 초를 state 에
 * 담아 1씩 늘리면 탭이 백그라운드로 갔을 때 타이머가 밀려 실제보다 적게 나옵니다.
 * 매 tick 마다 시작 시각과의 차이를 다시 구하면 복귀 시 바로 맞춰집니다.
 *
 * @param startedAt 시작 시각(ms). null 이면 0 을 돌려줍니다.
 */
export function useElapsedSeconds(startedAt: number | null): number {
  const now = useNow()

  if (startedAt === null) return 0
  return Math.max(0, Math.floor((now - startedAt) / 1000))
}
