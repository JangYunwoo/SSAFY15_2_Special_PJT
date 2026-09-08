import type { ReactNode } from 'react'

import { cx } from '@/shared/lib/cx'

interface DataRowProps {
  label: ReactNode
  /** 라벨 아래 작은 보조 설명 (.rowsub) */
  hint?: ReactNode | undefined
  children: ReactNode
  className?: string | undefined
}

/** 목업의 .rowline — 좌측 라벨 / 우측 값 한 줄 */
export function DataRow({ label, hint, children, className }: DataRowProps) {
  return (
    <div className={cx('rowline', className)}>
      <span>
        {label}
        {hint !== undefined && <span className="rowsub">{hint}</span>}
      </span>
      <b>{children}</b>
    </div>
  )
}

/** 합계 줄. 위에 굵은 구분선이 붙습니다 (.total) */
export function TotalRow({ label, children }: { label: ReactNode; children: ReactNode }) {
  return (
    <div className="total">
      <div className="rowline">
        <span>{label}</span>
        <b>{children}</b>
      </div>
    </div>
  )
}

/** 요약 패널 안의 구분 라벨 (.row-group) */
export function RowGroup({ children }: { children: ReactNode }) {
  return <div className="row-group">{children}</div>
}
