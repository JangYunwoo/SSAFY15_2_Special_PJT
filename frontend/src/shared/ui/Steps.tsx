import { cx } from '@/shared/lib/cx'
import { Link } from 'react-router'

interface StepsProps {
  steps: readonly string[]
  /** 0-based 현재 단계 */
  current: number
  /** 이동 가능한 단계만 목적지를 전달한다. 결제 등의 기본 표시는 바꾸지 않는다. */
  links?: readonly (string | null)[]
}

/** 결제·등록 플로우 상단의 단계 표시 (.steps) */
export function Steps({ steps, current, links }: StepsProps) {
  return (
    <ol className="steps" aria-label="진행 단계">
      {steps.map((step, index) => (
        <li
          key={step}
          className={cx(index === current && 'on')}
          aria-current={index === current ? 'step' : undefined}
        >
          {links?.[index] ? (
            <Link to={links[index]}>{index + 1}. {step}</Link>
          ) : <>{index + 1}. {step}</>}
        </li>
      ))}
    </ol>
  )
}
