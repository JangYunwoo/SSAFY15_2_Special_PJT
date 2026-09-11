import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'

import { paymentApi } from '@/entities/payment/api'
import { toMessage } from '@/shared/api/errors'
import { Button } from '@/shared/ui/Button'

type CallbackResult = 'success' | 'cancel' | 'fail'
type ViewState = 'processing' | 'cancelled' | 'failed' | 'error'

interface Props {
  result: CallbackResult
}

/** 카카오페이 인증 뒤 돌아오는 가맹점 화면(KAKA-02~04). */
export default function KakaoPayCallbackPage({ result }: Props) {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [viewState, setViewState] = useState<ViewState>('processing')
  const [message, setMessage] = useState('카카오페이 결제 결과를 확인하고 있습니다.')
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true

    const paymentId = searchParams.get('paymentId')
    const state = searchParams.get('state')
    const pgToken = searchParams.get('pg_token')

    const run = async () => {
      if (!paymentId || !state) {
        setViewState('error')
        setMessage('결제 확인 정보가 없습니다. 결제 내역에서 상태를 확인해 주세요.')
        return
      }

      try {
        if (result === 'success') {
          if (!pgToken) throw new Error('카카오페이 승인 토큰이 없습니다.')
          const payment = await paymentApi.kakaoApprove({ paymentId, pgToken, state })
          void navigate(`/checkout/complete/${payment.paymentId}`, { replace: true })
          return
        }

        if (result === 'cancel') {
          await paymentApi.kakaoCancelFlow(paymentId)
          setViewState('cancelled')
          setMessage('카카오페이 결제를 취소했습니다. 선점한 참여 자리도 반납했습니다.')
          return
        }

        await paymentApi.kakaoFailFlow(paymentId)
        setViewState('failed')
        setMessage('카카오페이 인증을 완료하지 못했습니다. 다시 결제해 주세요.')
      } catch (error) {
        // 승인 응답을 받은 뒤 화면만 다시 열린 경우에는 이미 PAID일 수 있다.
        if (result === 'success' && paymentId) {
          try {
            const payment = await paymentApi.detail(paymentId)
            if (payment.status === 'PAID') {
              void navigate(`/checkout/complete/${payment.paymentId}`, { replace: true })
              return
            }
          } catch {
            // 최초 오류를 사용자에게 보여준다.
          }
        }
        setViewState('error')
        setMessage(toMessage(error))
      }
    }

    void run()
  }, [navigate, result, searchParams])

  return (
    <main className="wrap narrow">
      <section className="section">
        <div className="panel center">
          <h2>
            {viewState === 'processing'
              ? '결제 확인 중'
              : viewState === 'cancelled'
                ? '결제 취소'
                : viewState === 'failed'
                  ? '인증 실패'
                  : '결제 확인 필요'}
          </h2>
          <p className="sub mt16" role={viewState === 'error' ? 'alert' : undefined}>
            {message}
          </p>
          {viewState !== 'processing' && (
            <Button
              className="mt24"
              onClick={() => {
                void navigate('/group-buys', { replace: true })
              }}
            >
              공동구매로 돌아가기
            </Button>
          )}
        </div>
      </section>
    </main>
  )
}
