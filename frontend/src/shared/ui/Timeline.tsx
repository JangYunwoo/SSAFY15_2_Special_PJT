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
 * 라벨은 자기 지점(i / 마지막)에 정확히 세웁니다. 전에는 space-between 으로
 * 늘어놓았는데, 그러면 라벨 상자의 폭에 따라 글자 가운데가 밀립니다. 3단계에서
 * 첫 라벨("정산계좌 인증")이 마지막("완료")보다 길어, 가운데 라벨이 막대가 찬
 * 지점보다 오른쪽에 서 있었습니다. 지점을 직접 지정하면 글자 길이와 무관합니다.
 *
 * 양 끝은 밖으로 넘치지 않게 안쪽으로 당깁니다 — 첫 라벨은 왼쪽 끝을, 마지막
 * 라벨은 오른쪽 끝을 막대 끝에 맞춥니다.
 */
export function Timeline({ steps }: { steps: readonly TimelineStep[] }) {
  /* 지나온 칸 중 마지막. 칸 사이 간격이 아니라 라벨 위치에 맞춰 채웁니다. */
  const reached = steps.reduce((last, step, index) => (step.state === 'todo' ? last : index), 0)
  const last = steps.length - 1
  const at = (index: number) => (last > 0 ? (index / last) * 100 : 100)

  return (
    <div className="track">
      <ol className="track-labels">
        {steps.map((step, index) => (
          <li
            key={step.label}
            /* 현재 단계는 aria-current 로만 표시합니다. 보이는 구분은 막대가 합니다 */
            className={cx(step.state !== 'todo' && 'on')}
            aria-current={step.state === 'now' ? 'step' : undefined}
            style={{ left: `${String(at(index))}%` }}
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
        <span style={{ width: `${String(at(reached))}%` }} />
      </div>
    </div>
  )
}

