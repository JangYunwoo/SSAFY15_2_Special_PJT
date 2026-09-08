import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'

import { PAYMENT_METHOD_LABEL } from '@/entities/model'
import { paymentQueries } from '@/entities/payment/queries'
import { useCheckoutStore } from '@/features/checkout/checkoutStore'
import { groupBuyNo, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { Steps } from '@/shared/ui/Steps'
import { QueryBoundary } from '@/shared/ui/states'

import { CHECKOUT_STEPS } from './steps'

export default function CompletePage() {
  useDocumentTitle('결제 완료')

  const { paymentId = '' } = useParams()
  const query = useQuery(paymentQueries.detail(paymentId))
  const reset = useCheckoutStore((state) => state.reset)

  // 결제가 끝났으니 단계 사이에 들고 있던 값을 비웁니다.
  useEffect(() => reset, [reset])

  return (
    <main className="wrap">
      <Steps steps={CHECKOUT_STEPS} current={2} />

      <QueryBoundary query={query}>
        {(payment) => (
          <section className="result">
            <div className="mark" aria-hidden="true">
              ✓
            </div>
            <h2>결제가 완료되었습니다</h2>
            <p>
              공동구매 {groupBuyNo(payment.groupBuyId)} · {PAYMENT_METHOD_LABEL[payment.method]}{' '}
              {won(payment.amount.totalAmount)}
              <br />
              목표 인원이 모이면 결제가 확정되고, 미달 시 전액 자동 환불됩니다.
              <br />
              결제 금액은 구매확정 전까지 에스크로에 보관됩니다.
            </p>

            <ButtonLink block to="/orders">
              거래 현황 확인하기
            </ButtonLink>
            <ButtonLink variant="ghost" className="mt8" style={{ display: 'block' }} to="/">
              다른 공동구매 둘러보기
            </ButtonLink>
          </section>
        )}
      </QueryBoundary>
    </main>
  )
}
