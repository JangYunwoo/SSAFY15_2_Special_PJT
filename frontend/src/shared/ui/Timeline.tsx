import { cx } from '@/shared/lib/cx'

export interface TimelineStep {
  label: string
  state: 'done' | 'now' | 'todo'
}

/** 거래관리의 5단계 타임라인 (.timeline) */
export function Timeline({ steps }: { steps: readonly TimelineStep[] }) {
  return (
    <ol className="timeline">
      {steps.map((step, index) => (
        <li
          key={step.label}
          className={cx('step', step.state === 'done' && 'done', step.state === 'now' && 'now')}
          aria-current={step.state === 'now' ? 'step' : undefined}
        >
          <div className="dot" aria-hidden="true">
            {step.state === 'done' ? '✓' : index + 1}
          </div>
          {step.label}
        </li>
      ))}
    </ol>
  )
}
