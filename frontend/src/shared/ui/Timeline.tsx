import { cx } from '@/shared/lib/cx'

export interface TimelineStep {
  label: string
  state: 'done' | 'now' | 'todo'
}

/**
 * 거래 진행 막대 (.track)
 *
 * 단계마다 칸을 하나씩 두고, 지나온 칸을 채웁니다. 점과 잇는 선으로 그리던
 * 것을 바꿨습니다. 점 방식은 어디까지 왔는지를 "몇 번째 점이 칠해졌나" 로
 * 세어야 알 수 있는데, 막대는 채워진 길이가 그대로 진행률로 읽힙니다.
 *
 * 칸을 나누고 사이를 벌린 이유는 단계가 이어진 양이 아니라 띄엄띄엄한
 * 상태이기 때문입니다. 하나로 이은 막대는 "배송 중 60%" 처럼 중간값이
 * 있는 것처럼 보입니다.
 */
export function Timeline({ steps }: { steps: readonly TimelineStep[] }) {
  return (
    <ol className="track">
      {steps.map((step) => (
        <li
          key={step.label}
          className={cx('track-step', step.state !== 'todo' && 'on', step.state === 'now' && 'now')}
          aria-current={step.state === 'now' ? 'step' : undefined}
        >
          {step.label}
          <span className="track-bar" aria-hidden="true" />
        </li>
      ))}
    </ol>
  )
}
