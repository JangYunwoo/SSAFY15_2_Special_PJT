import type { ReactNode } from 'react'

import { cx } from '@/shared/lib/cx'

interface PanelProps {
  title?: ReactNode | undefined
  /** 제목 아래 보조 설명 (.sub) */
  description?: ReactNode | undefined
  /** 제목 오른쪽 영역 (금액·상태 등) */
  aside?: ReactNode | undefined
  children?: ReactNode | undefined
  className?: string | undefined
}

/** 목업의 .panel — 화면을 구성하는 기본 카드 */
export function Panel({ title, description, aside, children, className }: PanelProps) {
  const heading = title !== undefined && (
    <div>
      <h3>{title}</h3>
      {description !== undefined && <p className="sub">{description}</p>}
    </div>
  )

  return (
    <section className={cx('panel', className)}>
      {aside !== undefined ? (
        <div className="order-head">
          {heading}
          {aside}
        </div>
      ) : (
        heading
      )}
      {children}
    </section>
  )
}
