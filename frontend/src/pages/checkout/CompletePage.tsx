import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router'

import { PAYMENT_METHOD_LABEL } from '@/entities/model'
import { paymentQueries } from '@/entities/payment/queries'
import { useCheckoutStore } from '@/features/checkout/checkoutStore'
import { queryKeys } from '@/shared/api/queryKeys'
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
  const queryClient = useQueryClient()

  // 결제가 끝났으니 단계 사이에 들고 있던 값을 비웁니다.
  useEffect(() => reset, [reset])

  /**
   * 결제로 달라진 화면들의 캐시를 버립니다.
   *
   * 전역 staleTime 이 60초라, 이 처리가 없으면 결제를 마치고 목록이나 상세로
   * 돌아가도 1분 동안 결제 전 참여 인원이 그대로 보입니다. 거래관리도 같습니다.
   * 실제로 "일정 시간 뒤에야 반영된다"는 지적이 있었습니다.
   *
   * 카드·코인·카카오페이 세 경로가 모두 이 화면으로 들어오므로 여기 한 곳에서
   * 처리합니다. 결제가 끝나는 지점마다 따로 넣으면 한 곳을 빠뜨리기 쉽습니다.
   *
   * 지금 화면에 없는 목록은 다시 불러오지 않고 "낡음"으로만 표시됩니다.
   * 사용자가 그 화면으로 이동할 때 새로 불러옵니다.
   */
  useEffect(() => {
    // refetchType:'none' — 지금 이 화면에 떠 있는 결제 상세까지 다시 부르지
    // 않도록 합니다. 방금 받아온 값이라 한 번 더 부를 이유가 없습니다.
    const forget = (queryKey: readonly unknown[]) =>
      queryClient.invalidateQueries({ queryKey, refetchType: 'none' })

    void forget(queryKeys.groupBuys.all)
    void forget(queryKeys.participations.all)
    void forget(queryKeys.payments.all)
  }, [queryClient, paymentId])

  return (
    <main className="wrap">
      <Steps steps={CHECKOUT_STEPS} current={2} />

      <QueryBoundary query={query}>
        {(payment) => (
          <section className="result">
            {/* 체크는 글자(✓)가 아니라 SVG 입니다. 글자는 폰트마다 굵기와 위치가 달라집니다 */}
            <div className="mark" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="30" height="30">
                <polyline
                  points="5 12.5 10 17.5 19 7"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.6"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </div>
            <h2>결제가 완료되었습니다</h2>
            <p>
              공동구매 {groupBuyNo(payment.groupBuyId)} · {PAYMENT_METHOD_LABEL[payment.method]}{' '}
              {won(payment.amount.totalAmount)}
            </p>

            <ButtonLink block className="result-go" to="/orders">
              거래 현황 확인하기
            </ButtonLink>
            <ButtonLink variant="ghost" block className="mt8" to="/">
              다른 공동구매 둘러보기
            </ButtonLink>
          </section>
        )}
      </QueryBoundary>
    </main>
  )
}

