import type { ParticipationStatus } from '@/entities/model'
import type { TimelineStep } from '@/shared/ui/Timeline'

/** 거래관리에 노출하는 정상 경로 5단계 */
const STAGES = ['결제완료', '모집완료', '배송', '배송완료', '구매확정'] as const

/** 각 상태가 도달한 단계 (0-based). 정상 경로를 벗어난 상태는 -1 로 둡니다. */
const STAGE_INDEX: Record<ParticipationStatus, number> = {
  PAID: 0,
  FULFILLED: 1,
  SHIPPING: 2,
  DELIVERED: 3,
  CONFIRMED: 4,
  SETTLED: 4,
  CANCELED: -1,
  RETURNING: -1,
  RETURNED: -1,
  REFUNDING: -1,
  REFUNDED: -1,
}

/**
 * 참여 상태를 타임라인 단계로 변환합니다.
 * 취소·반품·환불처럼 정상 경로를 벗어난 건은 결제완료까지만 채웁니다.
 */
export function participationTimeline(status: ParticipationStatus): TimelineStep[] {
  const reached = STAGE_INDEX[status]
  const current = reached === -1 ? 0 : reached

  return STAGES.map((label, index) => ({
    label,
    state:
      index < current || (index === current && (status === 'CONFIRMED' || status === 'SETTLED'))
        ? 'done'
        : index === current
          ? status === 'PAID' && index === 0
            ? 'done'
            : 'now'
          : 'todo',
  }))
}

/** 지난 거래로 접어둘 상태 */
export function isPastOrder(status: ParticipationStatus): boolean {
  return (
    status === 'CONFIRMED' ||
    status === 'SETTLED' ||
    status === 'CANCELED' ||
    status === 'RETURNED' ||
    status === 'REFUNDED'
  )
}
