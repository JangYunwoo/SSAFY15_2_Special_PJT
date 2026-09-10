import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'

import { GROUP_BUY_STATUS_DISPLAY } from '@/entities/model'
import type { Participant, SellerGroupBuySummary } from '@/entities/groupBuy/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { date, dateTime, groupBuyNo, maskName, won } from '@/shared/lib/format'
import { usePageParam } from '@/shared/lib/hooks/usePageParam'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Pagination } from '@/shared/ui/Pagination'
import { Panel } from '@/shared/ui/Panel'
import { ProgressBar } from '@/shared/ui/ProgressBar'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

/** 판매자 목록과 같은 조건. 캐시를 함께 써서 목록에서 들어오면 다시 부르지 않습니다. */
const SELLER_LIST_PARAMS = { size: 100 } as const

export default function SellerGroupBuyDetailPage() {
  useDocumentTitle('공동구매 상세')

  const { groupBuyId = '' } = useParams()
  const { page, goToPage } = usePageParam()

  /**
   * GRP-06(판매자 운영 상세)이 백엔드에 아직 없어 목록에서 해당 건을 골라 씁니다.
   * 엔드포인트가 생기면 groupBuyQueries.sellerDetail 로 바꾸면 됩니다.
   */
  const summary = useQuery({
    ...groupBuyQueries.sellerList(SELLER_LIST_PARAMS),
    // 공동구매 응답의 id 는 JSON number 로 옵니다(S15P21B205-121). URL 파라미터는
    // 항상 문자열이라 문자열로 맞춰 비교합니다.
    select: (result) => result.items.find((item) => String(item.id) === groupBuyId),
  })

  const participants = useQuery(groupBuyQueries.participants(groupBuyId, { page }))

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">공동구매 {groupBuyNo(groupBuyId)}</h2>
          <p className="sec-sub">모집 현황과 참여자를 확인합니다.</p>
        </div>
        <ButtonLink variant="ghost" to="/seller">
          목록으로
        </ButtonLink>
      </div>

      {/* 목록에 없는 id 면 select 가 undefined 를 돌려주고 QueryBoundary 가 빈 상태를 그립니다. */}
      <QueryBoundary query={summary}>
        {(found) => (
          <div className="split">
            <div>
              <Panel title="참여자 현황" description="결제를 마친 참여자 목록">
                <QueryBoundary query={participants}>
                  {(result) =>
                    result.items.length === 0 ? (
                      <EmptyState>아직 참여자가 없습니다.</EmptyState>
                    ) : (
                      <>
                        <ParticipantTable items={result.items} />
                        <Pagination
                          meta={result.meta}
                          onChange={goToPage}
                          label="참여자 목록 페이지"
                        />
                      </>
                    )
                  }
                </QueryBoundary>

                <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
                  참여자 이름은 가운데를 가려 표시합니다. 배송을 준비할 때는 주문 관리에서 송장을
                  등록하세요.
                </p>
              </Panel>
            </div>

            <aside className="sticky">
              <SummaryPanel item={found} />
            </aside>
          </div>
        )}
      </QueryBoundary>
    </section>
  )
}

function SummaryPanel({ item }: { item: SellerGroupBuySummary }) {
  const display = GROUP_BUY_STATUS_DISPLAY[item.status]

  return (
    <Panel title="모집 현황" description={item.productName}>
      <div className="mb16">
        <StateChip tone={display.tone}>{display.label}</StateChip>
      </div>

      <ProgressBar current={item.paidCount} target={item.targetCount} />

      <DataRow label="참여">
        {item.paidCount} / {item.targetCount}명
      </DataRow>
      <DataRow label="구매확정">{item.confirmedCount}건</DataRow>
      <DataRow label="상품 가격">{won(item.productPrice)}</DataRow>
      <DataRow label="1인 분담금">{won(item.unitShareAmount)}</DataRow>
      <DataRow label="배송비">{won(item.shippingFee)}</DataRow>
      <DataRow label="모집 마감">{date(item.recruitEndAt)}</DataRow>
      <DataRow label="송장 등록률">
        {item.invoiceRegisteredRate === null
          ? '배송 준비 전'
          : `${String(Math.round(item.invoiceRegisteredRate * 100))}%`}
      </DataRow>

      <ButtonLink variant="ghost" block className="mt16" to="/seller/orders">
        주문 관리로 이동
      </ButtonLink>
    </Panel>
  )
}

function ParticipantTable({ items }: { items: readonly Participant[] }) {
  return (
    <table className="list fixed">
      <colgroup>
        <col style={{ width: '18%' }} />
        <col style={{ width: '42%' }} />
        <col style={{ width: '22%' }} />
        <col style={{ width: '18%' }} />
      </colgroup>
      <thead>
        <tr>
          <th>참여자</th>
          <th>배송지</th>
          <th>결제 시각</th>
          <th className="num">결제금액</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={item.participationId}>
            <td>
              <b>{maskName(item.buyerName)}</b>
              <br />
              <span className="small muted">수령 {maskName(item.recipientName)}</span>
            </td>
            <td>
              ({item.zipcode}) {item.address1}
              <br />
              <span className="small muted">{item.address2}</span>
            </td>
            <td>
              <span className="small">{dateTime(item.paidAt)}</span>
            </td>
            <td className="num">{won(item.amount)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
