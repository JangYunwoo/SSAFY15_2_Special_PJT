import type { ReactNode } from 'react'

import { cx } from '@/shared/lib/cx'

/**
 * global.css 의 .state 색 변형.
 * neutral 은 무채색이고, 나머지는 IBM 상태 틴트를 씁니다.
 */
export type StateTone =
  | 'neutral'
  /** 모집 중 */
  | 'live'
  /** 에스크로 보관 */
  | 'keep'
  /** 배송 */
  | 'ship'
  /** 확정·완료 */
  | 'ok'
  /** 대기·부분 처리 */
  | 'hold'
  /** 판매자 조치 필요 */
  | 'alert'
  /** 반품 진행·종결 */
  | 'back'
  /** 마감 임박 (잔여 일수가 얼마 남지 않은 모집 건) */
  | 'urgent'

export function StateChip({
  tone = 'neutral',
  children,
}: {
  tone?: StateTone | undefined
  children: ReactNode
}) {
  return <span className={cx('state', tone !== 'neutral' && tone)}>{children}</span>
}
