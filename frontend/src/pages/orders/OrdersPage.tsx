import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'

import { addressQueries } from '@/entities/address/queries'
import type { ParticipationSummary } from '@/entities/participation/model'
import { participationQueries } from '@/entities/participation/queries'
import { OrderAccordion } from '@/features/orders/OrderAccordion'
import { OrdersTabs } from '@/features/orders/OrdersTabs'
import { isPastOrder } from '@/features/orders/participationTimeline'
import { phone, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

export default function OrdersPage() {
  useDocumentTitle('거래관리')

  const query = useQuery(participationQueries.list({ size: 100 }))

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

        <QueryBoundary query={query}>{(page) => <OrdersView items={page.items} />}</QueryBoundary>
      </section>
    </main>
  )
}

function OrdersView({ items }: { items: readonly ParticipationSummary[] }) {
  const addresses = useQuery(addressQueries.list())

  const { ongoing, past, summary } = useMemo(() => {
    const ongoingItems = items.filter((item) => !isPastOrder(item.status))
    const pastItems = items.filter((item) => isPastOrder(item.status))

    const held = items.filter((item) => item.escrowStatus === 'HELD')
    const released = items.filter((item) => item.escrowStatus === 'RELEASED')
    const refunding = items.filter(
      (item) => item.status === 'REFUNDING' || item.status === 'RETURNING',
    )
    const sum = (list: readonly ParticipationSummary[]) =>
      list.reduce((total, item) => total + item.totalAmount, 0)

    return {
      ongoing: ongoingItems,
      past: pastItems,
      summary: {
        heldCount: held.length,
        heldAmount: sum(held),
        releasedCount: released.length,
        releasedAmount: sum(released),
        refundingCount: refunding.length,
        refundingAmount: sum(refunding),
      },
    }
  }, [items])

  const defaultAddress = addresses.data?.find((item) => item.isDefault) ?? addresses.data?.[0]

  if (items.length === 0) {
    return <EmptyState>참여한 공동구매가 아직 없습니다.</EmptyState>
  }

  return (
    <div className="split">
      <div>
        {ongoing.map((item) => (
          <OrderAccordion key={item.id} item={item} defaultOpen />
        ))}

        {past.length > 0 && (
          <>
            <div className="list-title mt24">지난 거래 {past.length}건</div>
            {past.map((item) => (
              <OrderAccordion key={item.id} item={item} />
            ))}
          </>
        )}
      </div>

      <aside>
        <Panel title="나의 거래 현황" description="에스크로 보관 중">
          <div className="amount">{won(summary.heldAmount)}</div>
          <DataRow label="에스크로 보관">
            {summary.heldCount}건 · {won(summary.heldAmount)}
          </DataRow>
          <DataRow label="정산 완료">
            {summary.releasedCount}건 · {won(summary.releasedAmount)}
          </DataRow>
          <DataRow label="환불 진행">
            {summary.refundingCount}건 · {won(summary.refundingAmount)}
          </DataRow>
          <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
            에스크로 보관 금액은 구매확정 전까지 판매자에게 지급되지 않습니다.
          </p>
        </Panel>

        {defaultAddress && (
          <Panel className="mt16" title="기본 배송지" description="공동구매 상품 수령 주소">
            <p className="para">
              {defaultAddress.recipientName} · {phone(defaultAddress.recipientPhone)}
              <br />
              {defaultAddress.address1}
              <br />
              {defaultAddress.address2}
            </p>
            <ButtonLink variant="ghost" style={{ display: 'block' }} to="/orders">
              배송지 변경하기
            </ButtonLink>
          </Panel>
        )}
      </aside>
    </div>
  )
}
