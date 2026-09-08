import { useMemo, useState } from 'react'

import { clock, phone } from '@/shared/lib/format'
import { cx } from '@/shared/lib/cx'
import { useCountdown } from '@/shared/lib/hooks/useCountdown'
import { Button } from '@/shared/ui/Button'

const CODE_LENGTH = 6
const CODE_TTL_SECONDS = 180

interface VerificationCodeStepProps {
  phoneNumber: string
  onVerified: () => void
  onResend: () => void
  className?: string | undefined
}

/**
 * 인증번호 입력 단계.
 *
 * 본인확인기관 연동 API가 아직 없어 코드 검증은 서버로 보내지 않습니다.
 * 백엔드가 준비되면 handleVerify 안의 로컬 검사를 실제 요청으로 바꾸면 됩니다.
 */
export function VerificationCodeStep({
  phoneNumber,
  onVerified,
  onResend,
  className,
}: VerificationCodeStepProps) {
  const [issuedAt, setIssuedAt] = useState(() => Date.now())
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)

  const expiresAt = useMemo(
    () => new Date(issuedAt + CODE_TTL_SECONDS * 1000).toISOString(),
    [issuedAt],
  )
  const { secondsLeft, isExpired } = useCountdown(expiresAt)

  const verify = () => {
    if (isExpired) {
      setError('인증번호 유효시간이 지났습니다. 재전송해 주세요.')
      return
    }
    if (code.length !== CODE_LENGTH) {
      setError(`인증번호 ${String(CODE_LENGTH)}자리를 입력해 주세요.`)
      return
    }
    setError(null)
    onVerified()
  }

  const resend = () => {
    setIssuedAt(Date.now())
    setCode('')
    setError(null)
    onResend()
  }

  return (
    <section className={cx('panel', className)}>
      <h3>인증번호 입력</h3>
      <p className="sub">
        {phoneNumber ? `${phone(phoneNumber)} 로 보낸 ` : ''}
        {CODE_LENGTH}자리 숫자를 입력하세요
      </p>

      <div className="field-row">
        <input
          className="input"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={CODE_LENGTH}
          placeholder="000000"
          aria-label="인증번호"
          value={code}
          onChange={(event) => {
            setCode(event.target.value.replace(/\D/g, ''))
          }}
        />
        <div className="slot-clock" style={{ flex: '0 0 auto', alignSelf: 'center' }}>
          {clock(secondsLeft)}
        </div>
      </div>

      <div className="order-actions">
        <Button variant="ghost" onClick={resend}>
          재전송
        </Button>
        <Button onClick={verify} disabled={code.length !== CODE_LENGTH}>
          인증 확인
        </Button>
      </div>

      {error !== null && (
        <p className="ai-hint fail" role="alert">
          {error}
        </p>
      )}
    </section>
  )
}
