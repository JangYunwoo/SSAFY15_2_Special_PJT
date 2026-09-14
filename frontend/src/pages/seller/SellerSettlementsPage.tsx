import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import type { SellerProfile } from '@/entities/auth/model'
import { authQueries } from '@/entities/auth/queries'
import { SETTLEMENT_STATUS_DISPLAY } from '@/entities/model'
import { settlementQueries } from '@/entities/settlement/queries'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { date, groupBuyNo, number, percent, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { DataRow, RowGroup, TotalRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, PendingBackendState, QueryBoundary } from '@/shared/ui/states'

/** 정산 목록·상세를 부를 수 있는지. 백엔드 컨트롤러가 생기면 플래그만 켜면 됩니다. */
const SETTLEMENTS_READY = BACKEND_FEATURES.settlements

/**
 * 정산 관리.
 *
 * STL-01·02 는 백엔드에 아직 컨트롤러가 없습니다(`domain/settlement` 에
 * package-info.java 뿐). 금액을 지어내면 판매자가 실제 지급액으로 오해하므로
 * 조회 자체를 `enabled` 로 막고 준비 중임을 밝힙니다. 표를 그리는 코드는 그대로
 * 두어 엔드포인트가 붙는 즉시 되살아납니다.
 *
 * 정산 계좌는 SEL-06(구현 완료)으로 실제 등록값을 불러옵니다.
 */
export default function SellerSettlementsPage() {
  useDocumentTitle('정산 관리')

  // 훅을 조건부로 부르지 않고 enabled 로 요청만 막습니다 (TanStack Query 권장).
  const list = useQuery({ ...settlementQueries.list({ size: 100 }), enabled: SETTLEMENTS_READY })
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const items = list.data?.items ?? []
  const activeId =
    selectedId ??
    items.find((item) => item.status !== 'COMPLETED')?.settlementId ??
    items[0]?.settlementId ??
    null

  const pending = items.filter((item) => item.status === 'PENDING' || item.status === 'PARTIAL')
  const completed = items.filter((item) => item.status === 'COMPLETED')

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">정산 관리</h2>
          <p className="sec-sub">정산 예정 금액과 지급 내역을 확인합니다.</p>
        </div>
      </div>

      <div className="split">
        <div>
          <Panel title="정산 내역" description="공동구매 건별 정산 · 구매확정을 조건으로 개시">
            <div className="guide">
              <div className="guide-head">지급 일정 안내</div>
              <ul>
                <li>참여자가 구매확정하면 해당 건이 정산 대상에 포함됩니다.</li>
                <li>
                  배송완료 후 {POLICY_DEFAULTS.autoConfirmDays}일이 지나면 자동으로 구매확정
                  처리됩니다.
                </li>
                <li>
                  수동 구매확정을 하더라도 배송완료 후 {POLICY_DEFAULTS.settlementEligibleDays}일이
                  지나기 전에는 정산이 실행되지 않습니다.
                </li>
                <li>반품이 진행 중인 건은 처리가 끝날 때까지 정산에서 제외됩니다.</li>
              </ul>
            </div>

            {SETTLEMENTS_READY ? (
              <QueryBoundary query={list}>
                {(page) =>
                  page.items.length === 0 ? (
                    <EmptyState>정산 내역이 없습니다.</EmptyState>
                  ) : (
                    <table className="list fixed mt24">
                      <colgroup>
                        <col style={{ width: '28%' }} />
                        <col style={{ width: '13%' }} />
                        <col style={{ width: '19%' }} />
                        <col style={{ width: '13%' }} />
                        <col style={{ width: '27%' }} />
                      </colgroup>
                      <thead>
                        <tr>
                          <th>공동구매</th>
                          <th>참여</th>
                          <th>정산 가능 시점</th>
                          <th>상태</th>
                          <th className="num">정산 금액</th>
                        </tr>
                      </thead>
                      <tbody>
                        {page.items.map((item) => {
                          const display = SETTLEMENT_STATUS_DISPLAY[item.status]
                          return (
                            <tr key={item.settlementId}>
                              <td>
                                <button
                                  type="button"
                                  className="tlink"
                                  onClick={() => {
                                    setSelectedId(item.settlementId)
                                  }}
                                >
                                  <b>{item.productName}</b>
                                </button>
                                <br />
                                <span className="small muted">{groupBuyNo(item.groupBuyId)}</span>
                              </td>
                              <td>
                                {item.paidCount} / {item.targetCount}명
                              </td>
                              <td>
                                {item.settlementEligibleAt === null
                                  ? '구매확정 후 확정'
                                  : date(item.settlementEligibleAt)}
                              </td>
                              <td>
                                <StateChip tone={display.tone}>{display.label}</StateChip>
                              </td>
                              <td className="num">
                                <b>{won(item.netAmount)}</b>
                                <br />
                                <span className="small muted">
                                  {item.confirmedCount > 0
                                    ? `확정 ${String(item.confirmedCount)}건 기준`
                                    : '전원 확정 기준'}
                                </span>
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  )
                }
              </QueryBoundary>
            ) : (
              <div className="mt24">
                <PendingBackendState>
                  정산 조회(STL-01·02) 엔드포인트가 아직 없습니다. 에스크로 보관 현황은 공동구매
                  상세의 에스크로 패널(ESC-02)에서 실제 값으로 확인할 수 있습니다.
                </PendingBackendState>
              </div>
            )}
          </Panel>

          {SETTLEMENTS_READY && activeId !== null && (
            <SettlementDetailPanel settlementId={activeId} />
          )}

          <Panel className="mt16" title="수수료 안내" description="공구한 정산 기준">
            <DataRow label="플랫폼 수수료">
              분담금 합계의 {percent(POLICY_DEFAULTS.platformFeeRate)} · 판매자 부담
            </DataRow>
            <DataRow label="배송비">참여자 개별 부담 · 정산금액에 포함</DataRow>
            <DataRow label="정산 조건">참여자 구매확정</DataRow>
            <DataRow label="정산 가능 시점">
              배송완료 후 {POLICY_DEFAULTS.settlementEligibleDays}일 경과
            </DataRow>
            <DataRow label="미달 시 처리">전액 자동 환불</DataRow>
          </Panel>
        </div>

        <aside>
          {SETTLEMENTS_READY && (
            <Panel title="정산 요약" description="정산 예정 금액">
              <div className="amount">
                {won(pending.reduce((total, item) => total + item.netAmount, 0))}
              </div>

              <RowGroup>현재</RowGroup>
              <DataRow label="정산 예정" hint={pending[0]?.productName}>
                {pending.reduce((total, item) => total + item.confirmedCount, 0)}건 ·{' '}
                {won(pending.reduce((total, item) => total + item.netAmount, 0))}
              </DataRow>

              <RowGroup>누적</RowGroup>
              <DataRow label="지급 완료" hint={completed[0]?.productName}>
                {completed.reduce((total, item) => total + item.confirmedCount, 0)}건 ·{' '}
                {won(completed.reduce((total, item) => total + item.netAmount, 0))}
              </DataRow>
            </Panel>
          )}

          <SettlementAccountPanel className={SETTLEMENTS_READY ? 'mt16' : undefined} />
        </aside>
      </div>
    </section>
  )
}

/** SEL-06 으로 실제 등록된 정산 계좌를 보여줍니다. */
function SettlementAccountPanel({ className }: { className?: string | undefined }) {
  const profile = useQuery(authQueries.sellerMe())

  return (
    <Panel className={className} title="정산 계좌" description="등록된 입금 계좌">
      <QueryBoundary query={profile} loadingLabel="정산 계좌를 불러오는 중입니다.">
        {(seller) => <SettlementAccount seller={seller} />}
      </QueryBoundary>
    </Panel>
  )
}

function SettlementAccount({ seller }: { seller: SellerProfile }) {
  if (seller.ssafyAccountNo === null) {
    return (
      <p className="small muted" style={{ marginBottom: 0 }}>
        등록된 정산 계좌가 없습니다. 계좌를 등록해야 정산금을 받을 수 있습니다.
      </p>
    )
  }

  return (
    <>
      <DataRow label="계좌번호">{seller.ssafyAccountNo}</DataRow>
      <DataRow label="예금주">{seller.accountHolder ?? '-'}</DataRow>
      <DataRow label="예금주 확인">{seller.accountVerified ? '완료' : '미확인'}</DataRow>
      <DataRow label="상호">{seller.businessName}</DataRow>
      <p className="small muted mt16" style={{ marginBottom: 0 }}>
        SSAFY 금융망 계좌로 지급합니다. 계좌 변경(SEL-07)은 백엔드 연동 후 이 화면에서 할 수
        있습니다.
      </p>
    </>
  )
}

function SettlementDetailPanel({ settlementId }: { settlementId: string }) {
  const query = useQuery(settlementQueries.detail(settlementId))

  return (
    <QueryBoundary query={query}>
      {(detail) => {
        const excluded = detail.items.filter((item) => !item.included)

        return (
          <Panel
            className="mt16"
            title={`정산 상세 · ${detail.productName}`}
            description={`${groupBuyNo(detail.groupBuyId)} · 참여 ${String(detail.paidCount)}건 중 확정 ${String(detail.confirmedCount)}건`}
          >
            <DataRow label="참여 건수">{detail.paidCount}건</DataRow>
            <DataRow label="구매확정 건수">{detail.confirmedCount}건</DataRow>
            <DataRow
              label={
                <>
                  반품 <span className="small muted">정산 제외</span>
                </>
              }
            >
              {excluded.length}건
            </DataRow>

            <DataRow
              className="mt16"
              label={
                <>
                  상품 분담금 합계 <span className="small muted">절상 차액 포함</span>
                </>
              }
            >
              {won(detail.shareAmountTotal)}
            </DataRow>
            <DataRow label="배송비 합계">{won(detail.shippingFeeTotal)}</DataRow>
            <DataRow
              label={
                <>
                  └ 절상 차액 <span className="small muted">판매자 귀속</span>
                </>
              }
            >
              {won(detail.roundingSurplus)}
            </DataRow>
            <DataRow
              label={
                <>
                  플랫폼 수수료{' '}
                  <span className="small muted">분담금의 {percent(detail.platformFeeRate)}</span>
                </>
              }
            >
              − {won(detail.platformFee)}
            </DataRow>
            <TotalRow label="정산금액">{won(detail.netAmount)}</TotalRow>

            {detail.holdReason !== null && (
              <p className="ai-hint fail">보류 사유 · {detail.holdReason}</p>
            )}

            <p className="small muted mt16" style={{ marginBottom: 0 }}>
              반품 {number(excluded.length)}건은 정산 대상에서 빠졌습니다. 다른 참여자의 분담금은
              재산정하지 않습니다.
            </p>
          </Panel>
        )
      }}
    </QueryBoundary>
  )
}
