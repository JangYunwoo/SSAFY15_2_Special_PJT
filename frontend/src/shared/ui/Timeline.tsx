import { cx } from '@/shared/lib/cx'

export interface TimelineStep {
  label: string
  state: 'done' | 'now' | 'todo'
}

/**
 * 거래 진행 막대 (.track)
 *
 * 끊기지 않은 막대 하나를 깔고, 지금 단계까지 채웁니다.
 *
 * 라벨은 양 끝을 막대 끝에 맞추고 나머지를 그 사이에 고르게 놓습니다.
 * 그래서 첫 단계는 0%, 마지막 단계는 100% 지점에 서고, 채워진 길이가
 * 곧 "몇 번째 라벨까지 왔는가" 가 됩니다. 칸을 나눠 채우면 라벨과 칸의
 * 경계가 어긋나 어느 쪽이 현재인지 한 번 더 따져야 합니다.
 */
export function Timeline({ steps }: { steps: readonly TimelineStep[] }) {
  /* 지나온 칸 중 마지막. 칸 사이 간격이 아니라 라벨 위치에 맞춰 채웁니다. */
  const reached = steps.reduce((last, step, index) => (step.state === 'todo' ? last : index), 0)
  const percent = steps.length > 1 ? (reached / (steps.length - 1)) * 100 : 100

  return (
    <div className="track">
      <ol className="track-labels">
        {steps.map((step) => (
          <li
            key={step.label}
            className={cx(step.state !== 'todo' && 'on', step.state === 'now' && 'now')}
            aria-current={step.state === 'now' ? 'step' : undefined}
          >
            {step.label}
          </li>
        ))}
      </ol>
      {/*
        진행률은 라벨이 이미 글로 말하고 있어 읽어줄 것이 없습니다.
        첫 단계(0%)에서도 막대가 빈 줄로 보이지 않게 최소 폭을 둡니다.
      */}
      <div className="track-bar" aria-hidden="true">
        <span style={{ width: `${String(percent)}%` }} />
      </div>
    </div>
  )
}
