import { GROUP_BUY_STATUS_DISPLAY } from '@/entities/model'
import type { GroupBuySummary } from '@/entities/groupBuy/model'
import type { Display } from '@/entities/statusDisplay'
import { displayFor } from '@/entities/statusDisplay'
import { POLICY_DEFAULTS } from '@/shared/config/policy'

/**
 * 모집 마감이 코앞인가.
 *
 * 뱃지 색과 카운트다운 노출이 같은 기준을 써야 합니다. 한쪽만 "마감 임박" 이라고
 * 하고 다른 쪽은 아무 일 없다는 듯 굴면 화면이 스스로 모순됩니다.
 */
export function isImminent(item: GroupBuySummary, now: Date = new Date()): boolean {
  if (item.status !== 'RECRUITING') return false
  const msLeft = new Date(item.recruitEndAt).getTime() - now.getTime()
  if (msLeft <= 0) return false
  return Math.ceil(msLeft / 86_400_000) <= POLICY_DEFAULTS.imminentDays
}

/**
 * 목록 카드에 붙는 상태 뱃지 (S15P21B205-226).
 *
 * 공유 표(GROUP_BUY_STATUS_DISPLAY)를 그대로 쓰지 않습니다. 목록에서는 남은
 * 기간이 선택을 좌우하므로 RECRUITING 을 "모집중"과 "마감 임박" 둘로 쪼개
 * 보여줘야 합니다. 상세·판매자 화면의 표기까지 같이 바뀌면 곤란해서 여기서만
 * 갈라 쓰고, 나머지 상태는 공유 표에 그대로 위임합니다.
 *
 * 기한이 지났는데 서버 상태가 아직 RECRUITING 인 경우가 있습니다. 마감 처리는
 * 배치로 도는데 목록은 그 사이에도 조회되기 때문입니다. 그 틈에 "모집중"을
 * 보여주면 이미 닫힌 건에 사용자가 들어갑니다. 그래서 시간으로 먼저 거릅니다.
 */
export function cardStatusDisplay(item: GroupBuySummary, now: Date = new Date()): Display {
  if (item.status !== 'RECRUITING') {
    return displayFor(GROUP_BUY_STATUS_DISPLAY, item.status, '공동구매 상태')
  }

  const msLeft = new Date(item.recruitEndAt).getTime() - now.getTime()
  if (msLeft <= 0) return { label: '마감', tone: 'alert' }

  const daysLeft = Math.ceil(msLeft / 86_400_000)
  if (daysLeft <= POLICY_DEFAULTS.imminentDays) return { label: '마감 임박', tone: 'urgent' }

  return { label: '모집중', tone: 'neutral' }
}

