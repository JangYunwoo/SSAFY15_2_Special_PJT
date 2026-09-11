import type { GroupBuySummary } from '@/entities/groupBuy/model'

const IMMINENT_HOURS = 12
const NEW_DAYS = 3

/**
 * 카드 좌상단 라벨.
 * 목업은 각 상품에 tag 를 직접 적어뒀지만, 실제로는 서버가 주는
 * 마감 시각·개설 시각에서 파생되는 값이라 여기서 계산합니다.
 */
export function statusTag(item: GroupBuySummary, now: Date = new Date()): string {
  if (item.status !== 'RECRUITING') return '모집종료'

  const hoursLeft = (Date.parse(item.recruitEndAt) - now.getTime()) / 3_600_000
  if (hoursLeft <= IMMINENT_HOURS) return '마감 임박'

  const daysOpen = (now.getTime() - Date.parse(item.createdAt)) / 86_400_000
  if (daysOpen <= NEW_DAYS) return '신규'

  return '모집중'
}
