import { cx } from '@/shared/lib/cx'

import type { AgreementState } from './useAgreements'

interface AgreementListProps {
  terms: readonly string[]
  state: AgreementState
  className?: string | undefined
}

/** 약관 동의 목록 (.agree) */
export function AgreementList({ terms, state, className }: AgreementListProps) {
  return (
    <div className={cx('agree', className)}>
      <label className="agree-all">
        <input type="checkbox" checked={state.allChecked} onChange={state.toggleAll} />
        <span>약관에 모두 동의합니다</span>
      </label>
      {terms.map((term, index) => (
        <label key={term} className="agree-row">
          <input
            type="checkbox"
            checked={state.checked[index] ?? false}
            onChange={() => {
              state.toggle(index)
            }}
          />
          <span>
            {term} <b>필수</b>
          </span>
        </label>
      ))}
    </div>
  )
}
