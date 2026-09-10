import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'

import { GROUP_BUY_STATUS_DISPLAY } from '@/entities/model'
import type { SellerGroupBuySummary } from '@/entities/groupBuy/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { returnQueries } from '@/entities/returnRequest/queries'
import { settlementQueries } from '@/entities/settlement/queries'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { date, dday, groupBuyNo, number, percent, remaining, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { DataRow, RowGroup } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { ProgressBar } from '@/shared/ui/ProgressBar'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

export default function SellerHomePage() {
  useDocumentTitle('공동구매 관리')

  const groupBuys = useQuery(groupBuyQueries.sellerList({ size: 100 }))
  const settlements = useQuery(settlementQueries.list({ size: 100 }))
  const returns = useQuery(returnQueries.sellerList({ size: 100 }))

  const recruiting = groupBuys.data?.items.filter((item) => item.status === 'RECRUITING') ?? []
  const pendingSettlement =
    settlements.data?.items.filter((item) => item.status !== 'COMPLETED') ?? []
  const escrowHeld = recruiting.reduce(
    (total, item) => total + (item.unitShareAmount + item.shippingFee) * item.paidCount,
    0,
  )
  const openReturns = returns.data?.items.filter((item) => item.status === 'REQUESTED') ?? []
  const pickupReturns = returns.data?.items.filter((item) => item.status === 'APPROVED') ?? []

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">공동구매 관리</h2>
          <p className="sec-sub">
            등록한 공동구매 {number(groupBuys.data?.meta.totalElements ?? 0)}건
          </p>
        </div>
        <ButtonLink variant="ghost" to="/seller/group-buys/new">
          공동구매 생성하기
        </ButtonLink>
      </div>

      <div className="split">
        <div>
          <Panel title="진행 중" description="모집이 끝나면 결제가 확정됩니다">
            {recruiting.length === 0 ? (
              <EmptyState>모집 중인 공동구매가 없습니다.</EmptyState>
            ) : (
              recruiting.map((item) => <RecruitingCard key={item.id} item={item} />)
            )}
          </Panel>

          <Panel className="mt16" title="전체 공동구매" description="모집 · 배송 · 정산 상태">
            <QueryBoundary query={groupBuys}>
              {(page) => (
                <table className="list fixed">
                  <colgroup>
                    <col style={{ width: '32%' }} />
                    <col style={{ width: '14%' }} />
                    <col style={{ width: '14%' }} />
                    <col style={{ width: '20%' }} />
                    <col style={{ width: '20%' }} />
                  </colgroup>
                  <thead>
                    <tr>
                      <th>공동구매</th>
                      <th>상태</th>
                      <th>참여</th>
                      <th className="num">상품 가격</th>
                      <th className="num">1인 분담금</th>
                    </tr>
                  </thead>
                  <tbody>
                    {page.items.map((item) => {
                      const display = GROUP_BUY_STATUS_DISPLAY[item.status]
                      return (
                        <tr key={item.id}>
                          <td>
                            <Link className="link" to={`/seller/group-buys/${item.id}`}>
                              <b>{item.productName}</b>
                            </Link>
                            <br />
                            <span className="small muted">
                              {groupBuyNo(item.id)} · 마감 {date(item.recruitEndAt)}
                            </span>
                          </td>
                          <td>
                            <StateChip tone={display.tone}>{display.label}</StateChip>
                          </td>
                          <td>
                            {item.paidCount} / {item.targetCount}명
                          </td>
                          <td className="num">{won(item.productPrice)}</td>
                          <td className="num">
                            <b>{won(item.unitShareAmount)}</b>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              )}
            </QueryBoundary>

            <p className="small muted mt16" style={{ marginBottom: 0 }}>
              1인 분담금은 상품 가격을 목표 인원으로 나눈 금액입니다. 배송비는 참여자가 따로
              부담하며, 정산 시 합산되어 지급됩니다. 플랫폼 수수료는 분담금 합계의{' '}
              {percent(POLICY_DEFAULTS.platformFeeRate)}입니다.
            </p>
          </Panel>
        </div>

        <aside>
          <Panel title="판매 현황" description="에스크로 보관 총액">
            <div className="amount">{won(escrowHeld)}</div>

            <RowGroup>현재</RowGroup>
            <DataRow label="진행 중 공동구매" hint={recruiting[0]?.productName}>
              {recruiting.length}건
            </DataRow>
            <DataRow label="에스크로 보관중" hint="모집 중">
              {recruiting.reduce((total, item) => total + item.paidCount, 0)}건 · {won(escrowHeld)}
            </DataRow>
            <DataRow label="정산 예정" hint={pendingSettlement[0]?.productName}>
              {pendingSettlement.reduce((total, item) => total + item.confirmedCount, 0)}건 ·{' '}
              {won(pendingSettlement.reduce((total, item) => total + item.netAmount, 0))}
            </DataRow>
            <DataRow label="반품 진행">{openReturns.length + pickupReturns.length}건</DataRow>
          </Panel>

          <Panel className="mt16" title="처리할 일" description="지금 확인이 필요한 항목">
            {openReturns.length === 0 && pickupReturns.length === 0 ? (
              <p className="small muted" style={{ marginBottom: 0 }}>
                처리할 항목이 없습니다.
              </p>
            ) : (
              <>
                {openReturns.length > 0 && (
                  <Link className="rowline link" to="/seller/returns">
                    <span>
                      반품 승인 대기
                      <span className="rowsub">자동승인 {dday(openReturns[0]?.autoApproveAt)}</span>
                    </span>
                    <b>
                      {openReturns.length}건<span className="go">›</span>
                    </b>
                  </Link>
                )}
                {pickupReturns.length > 0 && (
                  <Link className="rowline link" to="/seller/returns">
                    <span>반품 수거 확인</span>
                    <b>
                      {pickupReturns.length}건<span className="go">›</span>
                    </b>
                  </Link>
                )}
              </>
            )}
          </Panel>
        </aside>
      </div>
    </section>
  )
}

function RecruitingCard({ item }: { item: SellerGroupBuySummary }) {
  return (
    <div className="stat-box">
      <div className="small muted">
        {item.productName} · {groupBuyNo(item.id)}
      </div>
      <div className="stat-count">
        {item.paidCount} / {item.targetCount}명
      </div>
      <ProgressBar current={item.paidCount} target={item.targetCount} />
      <div className="box rows mt16">
        <DataRow label="모집 마감">{remaining(item.recruitEndAt)}</DataRow>
        <DataRow label="상품 가격 ÷ 목표 인원">
          {number(item.productPrice)}원 ÷ {item.targetCount}명
        </DataRow>
        <DataRow label="확정 분담금">{won(item.unitShareAmount)}</DataRow>
      </div>
      <ButtonLink variant="ghost" block className="mt16" to="/seller/orders">
        참여자 주문 보기
      </ButtonLink>
    </div>
  )
}
