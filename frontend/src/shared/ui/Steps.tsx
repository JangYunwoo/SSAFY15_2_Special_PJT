import { cx } from '@/shared/lib/cx'

interface StepsProps {
  steps: readonly string[]
  /** 0-based 현재 단계 */
  current: number
}

/** 결제·등록 플로우 상단의 단계 표시 (.steps) */
export function Steps({ steps, current }: StepsProps) {
  return (
    <ol className="steps" aria-label="진행 단계">
      {steps.map((step, index) => (
        <li
          key={step}
          className={cx(index === current && 'on')}
          aria-current={index === current ? 'step' : undefined}
        >
          {index + 1}. {step}
        </li>
      ))}
    </ol>
  )
}
