import { useQuery } from '@tanstack/react-query'

import { PAYMENT_METHOD_LABEL, PAYMENT_STATUS_DISPLAY } from '@/entities/model'
import type { PaymentHistoryItem } from '@/entities/payment/model'
import { paymentQueries } from '@/entities/payment/queries'
import { OrdersTabs } from '@/features/orders/OrdersTabs'
import { clock, groupBuyNo, won } from '@/shared/lib/format'
import { usePageParam } from '@/shared/lib/hooks/usePageParam'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Pagination } from '@/shared/ui/Pagination'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

export default function OrdersPaymentsPage() {
  useDocumentTitle('결제 내역')

  const { page, goToPage } = usePageParam()
  const query = useQuery(paymentQueries.history({ page }))

  return (
    <main className="wrap">
      <section className="section">
        <div className="sec-head">
          <div>
            <h2 className="sec-title">거래관리</h2>
            <p className="sec-sub">참여한 공동구매의 진행 상태와 결제 내역을 확인합니다.</p>
          </div>
        </div>

        <OrdersTabs />

        <div className="panel">
          <QueryBoundary query={query}>
            {(result) =>
              result.items.length === 0 ? (
                <EmptyState>결제한 내역이 없습니다.</EmptyState>
              ) : (
                <>
                  <PaymentTable items={result.items} />
                  <Pagination meta={result.meta} onChange={goToPage} label="결제 내역 페이지" />
                </>
              )
            }
          </QueryBoundary>

          <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
            참여 내역에는 결제를 마친 건만 나옵니다. 승인에 실패했거나 시간이 지나 취소된 결제는
            이곳에서만 확인할 수 있습니다.
          </p>
        </div>
      </section>
    </main>
  )
}

function PaymentTable({ items }: { items: readonly PaymentHistoryItem[] }) {
  return (
    <table className="list fixed">
      <colgroup>
        <col style={{ width: '26%' }} />
        <col style={{ width: '18%' }} />
        <col style={{ width: '22%' }} />
        <col style={{ width: '16%' }} />
        <col style={{ width: '18%' }} />
      </colgroup>
      <thead>
        <tr>
          <th>공동구매</th>
          <th>결제수단</th>
          <th>상태</th>
          <th className="num">결제금액</th>
          <th className="num">남은 시간</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => {
          const display = PAYMENT_STATUS_DISPLAY[item.status]
          const processing = item.status === 'PROCESSING'

          return (
            <tr key={item.paymentId}>
              <td>
                <b>{groupBuyNo(item.groupBuyId)}</b>
                <br />
                <span className="small muted">결제번호 {item.paymentId}</span>
              </td>
              <td>{PAYMENT_METHOD_LABEL[item.method]}</td>
              <td>
                <StateChip tone={display.tone}>{display.label}</StateChip>
              </td>
              <td className="num">{won(item.amount.totalAmount)}</td>
              <td className="num">
                {/* 남은 시간은 결제가 진행 중일 때만 뜻이 있습니다. */}
                {processing ? clock(item.remainingSeconds) : <span className="small muted">-</span>}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
