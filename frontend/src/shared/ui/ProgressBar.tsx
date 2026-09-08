import { progressRate } from '@/shared/config/policy'

interface ProgressBarProps {
  current: number
  target: number
  /** 스크린리더용 설명. 기본값은 "N명 참여 / 목표 M명" */
  label?: string | undefined
}

/** 모집 진행률 바 (.bar) */
export function ProgressBar({ current, target, label }: ProgressBarProps) {
  return (
    <div
      className="bar"
      role="progressbar"
      aria-valuenow={current}
      aria-valuemin={0}
      aria-valuemax={target}
      aria-label={label ?? `${String(current)}명 참여 / 목표 ${String(target)}명`}
    >
      <span style={{ width: `${String(progressRate(current, target))}%` }} />
    </div>
  )
}
