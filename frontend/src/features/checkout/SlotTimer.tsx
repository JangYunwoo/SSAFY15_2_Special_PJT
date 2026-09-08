import { useEffect } from 'react'

import { clock } from '@/shared/lib/format'
import { cx } from '@/shared/lib/cx'
import { useCountdown } from '@/shared/lib/hooks/useCountdown'

const WARN_SECONDS = 60

interface SlotTimerProps {
  expiresAt: string
  /** 만료되면 한 번 호출됩니다. 슬롯을 정리하고 상세로 되돌릴 때 씁니다. */
  onExpire?: (() => void) | undefined
}

/** 결제 버튼 아래의 참여 자리 확보 타이머 (.slot) */
export function SlotTimer({ expiresAt, onExpire }: SlotTimerProps) {
  const { secondsLeft, isExpired } = useCountdown(expiresAt)

  useEffect(() => {
    if (isExpired) onExpire?.()
    // onExpire 는 렌더마다 새로 만들어질 수 있어 만료 여부만 관찰합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isExpired])

  return (
    <div
      className={cx(
        'slot',
        isExpired && 'expired',
        !isExpired && secondsLeft <= WARN_SECONDS && 'warn',
      )}
      role="timer"
      aria-live={secondsLeft <= WARN_SECONDS ? 'polite' : 'off'}
    >
      <div className="slot-body">
        <div className="slot-title">
          {isExpired ? '자리가 해제되었습니다' : '참여 자리 확보 중'}
        </div>
        <div className="small muted">
          {isExpired ? '다시 참여를 시도해 주세요' : '시간이 지나면 자리가 해제됩니다'}
        </div>
      </div>
      <div className="slot-clock">{clock(secondsLeft)}</div>
    </div>
  )
}
