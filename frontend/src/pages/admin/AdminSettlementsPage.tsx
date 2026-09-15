import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { adminApi } from '@/entities/admin/api'
import { adminQueries } from '@/entities/admin/queries'
import {
  SETTLEMENT_STATUS_DISPLAY,
  TRANSFER_STATUS_DISPLAY,
  type SettlementStatus,
} from '@/entities/model'
import { displayFor } from '@/entities/statusDisplay'
import type { Settlement } from '@/entities/settlement/model'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { date, dateTime, groupBuyNo, number, percent, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { usePageParam } from '@/shared/lib/hooks/usePageParam'
import { Button } from '@/shared/ui/Button'
import { ChipFilter } from '@/shared/ui/Chips'
import { DataRow, TotalRow } from '@/shared/ui/DataRow'
import { TextAreaField } from '@/shared/ui/Field'
import { Pagination } from '@/shared/ui/Pagination'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

type Filter = 'ALL' | SettlementStatus

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'PENDING', label: '정산대기' },
  { value: 'HOLD', label: '정산보류' },
  { value: 'COMPLETED', label: '정산완료' },
]

/**
 * STL-03~07 정산 관리.
 *
 * 판매자 정산 화면(`SellerSettlementsPage`)과 보는 값은 같지만 할 수 있는 일이
 * 다릅니다. 실행·보류·재처리는 실제 계좌이체를 부르기 때문에 판매자에게는
 * 열지 않습니다 — 판매자가 스스로 지급을 일으킬 수 있으면 검증을 우회합니다.
 *
 * 한 건을 고르면 오른쪽에 상세와 조치 버튼이 뜹니다. 목록에 버튼을 늘어놓지
 * 않은 이유는, 실행이 돈을 내보내는 동작이라 "어느 건에 눌렀는지" 가 화면에
 * 분명히 남아야 하기 때문입니다.
 */
export default function AdminSettlementsPage() {
  useDocumentTitle('정산 관리')

  const { page, goToPage } = usePageParam()
  const [filter, setFilter] = useState<Filter>('ALL')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const query = useQuery(
    adminQueries.settlements({ page, size: 20, ...(filter === 'ALL' ? {} : { status: filter }) }),
  )

  const changeFilter = (next: Filter) => {
    setFilter(next)
    setSelectedId(null)
    // 필터가 바뀌면 지금 보던 쪽 번호는 의미가 없습니다.
    goToPage(0)
  }

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">정산 관리</h2>
          <p className="sec-sub">
            판매자 지급을 실행하고, 분쟁이 있는 건은 보류합니다. 실행·재처리는 실제 계좌이체를
            일으킵니다.
          </p>
        </div>
      </div>

      <ChipFilter label="정산 상태" value={filter} onChange={changeFilter} options={FILTERS} />

      <div className="split">
        <div>
          <QueryBoundary query={query} loadingLabel="정산 목록을 불러오는 중입니다.">
            {(result) =>
              result.items.length === 0 ? (
                <EmptyState>해당 상태의 정산 건이 없습니다.</EmptyState>
              ) : (
                <>
                  <div className="table-scroll">
                    <table className="list">
                      <thead>
                        <tr>
                          <th>공동구매</th>
                          <th>판매자</th>
                          <th>정산 상태</th>
                          <th>이체</th>
                          <th className="num">정산 금액</th>
                        </tr>
                      </thead>
                      <tbody>
                        {result.items.map((item) => (
                          <SettlementRow
                            key={item.settlementId}
                            item={item}
                            selected={item.settlementId === selectedId}
                            onSelect={() => {
                              setSelectedId(item.settlementId)
                            }}
                          />
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Pagination meta={result.meta} onChange={goToPage} label="정산 목록 페이지" />
                </>
              )
            }
          </QueryBoundary>
        </div>

        <aside>
          {selectedId === null ? (
            <Panel title="정산 상세" description="목록에서 한 건을 고르면 조치할 수 있습니다">
              <EmptyState>선택된 정산 건이 없습니다.</EmptyState>
            </Panel>
          ) : (
            <SettlementDetailPanel settlementId={selectedId} />
          )}
        </aside>
      </div>
    </section>
  )
}

function SettlementRow({
  item,
  selected,
  onSelect,
}: {
  item: Settlement
  selected: boolean
  onSelect: () => void
}) {
  const status = displayFor(SETTLEMENT_STATUS_DISPLAY, item.status, '정산 상태')
  const transfer = displayFor(TRANSFER_STATUS_DISPLAY, item.transferStatus, '이체 상태')

  return (
    <tr style={selected ? { background: 'var(--soft)' } : undefined}>
      <td>
        <button type="button" className="tlink" onClick={onSelect}>
          <b>{groupBuyNo(item.groupBuyId)}</b>
        </button>
        <br />
        <span className="small muted">{item.roundNo}회차</span>
      </td>
      <td>
        <span className="mono">#{item.sellerId}</span>
        <br />
        <span className="small muted">확정 {number(item.confirmedCount)}건</span>
      </td>
      <td>
        <StateChip tone={status.tone}>{status.label}</StateChip>
      </td>
      <td>
        <StateChip tone={transfer.tone}>{transfer.label}</StateChip>
        <br />
        <span className="small muted">{date(item.transferredAt)}</span>
      </td>
      <td className="num">
        <b>{won(item.netAmount)}</b>
        <br />
        <span className="small muted">수수료 {won(item.platformFee)}</span>
      </td>
    </tr>
  )
}

/** 실행·재처리처럼 돈이 나가는 조치는 한 번 더 확인시킵니다. */
type Pending = 'execute' | 'retry' | 'hold' | 'release' | null

function SettlementDetailPanel({ settlementId }: { settlementId: string }) {
  const queryClient = useQueryClient()
  const query = useQuery(adminQueries.settlement(settlementId))

  const [pending, setPending] = useState<Pending>(null)
  const [holdReason, setHoldReason] = useState('')

  const invalidate = async () => {
    setPending(null)
    setHoldReason('')
    await queryClient.invalidateQueries({ queryKey: queryKeys.admin.all })
  }

  const execute = useMutation({
    mutationFn: () => adminApi.executeSettlement(settlementId),
    onSuccess: invalidate,
  })
  const retry = useMutation({
    mutationFn: () => adminApi.retrySettlement(settlementId),
    onSuccess: invalidate,
  })
  const hold = useMutation({
    mutationFn: () => adminApi.holdSettlement(settlementId, holdReason),
    onSuccess: invalidate,
  })
  const release = useMutation({
    mutationFn: () => adminApi.releaseSettlementHold(settlementId),
    onSuccess: invalidate,
  })

  const error = execute.error ?? retry.error ?? hold.error ?? release.error
  const busy = execute.isPending || retry.isPending || hold.isPending || release.isPending

  return (
    <QueryBoundary query={query} loadingLabel="정산 상세를 불러오는 중입니다.">
      {({ settlement, items }) => {
        const excluded = items.filter((item) => !item.included)
        const status = displayFor(SETTLEMENT_STATUS_DISPLAY, settlement.status, '정산 상태')
        const transfer = displayFor(TRANSFER_STATUS_DISPLAY, settlement.transferStatus, '이체 상태')

        const canExecute =
          settlement.status === 'PENDING' && settlement.transferStatus === 'PENDING'
        const canRetry = settlement.status === 'PENDING' && settlement.transferStatus === 'FAILED'
        const canHold = settlement.status === 'PENDING'
        const canRelease = settlement.status === 'HOLD'

        return (
          <Panel
            title={`정산 ${groupBuyNo(settlement.groupBuyId)}`}
            description={`판매자 #${settlement.sellerId} · ${String(settlement.roundNo)}회차`}
            aside={
              <div className="order-amount">
                {won(settlement.netAmount)}
                <span className="small muted">지급 예정액</span>
              </div>
            }
          >
            <div className="box rows">
              <DataRow label="정산 상태">
                <StateChip tone={status.tone}>{status.label}</StateChip>
              </DataRow>
              <DataRow label="이체 상태">
                <StateChip tone={transfer.tone}>{transfer.label}</StateChip>
              </DataRow>
              <DataRow label="이체 시각">{dateTime(settlement.transferredAt)}</DataRow>
              <DataRow label="생성 시각">{dateTime(settlement.createdAt)}</DataRow>
            </div>

            {settlement.transferFailReason !== null && (
              <p className="ai-hint fail" role="alert">
                {settlement.status === 'HOLD' ? '보류 사유' : '이체 실패'} ·{' '}
                {settlement.transferFailReason}
              </p>
            )}

            <DataRow className="mt16" label="구매확정 건수">
              {number(settlement.confirmedCount)}건
            </DataRow>
            <DataRow label="정산 제외" hint="반품 등으로 빠진 건">
              {number(excluded.length)}건
            </DataRow>
            <DataRow label="상품 분담금 합계">{won(settlement.shareAmountTotal)}</DataRow>
            <DataRow label="배송비 합계">{won(settlement.shippingFeeTotal)}</DataRow>
            <DataRow label="└ 절상 차액" hint="판매자 귀속">
              {won(settlement.roundingSurplus)}
            </DataRow>
            <DataRow
              label="플랫폼 수수료"
              hint={`분담금의 ${percent(settlement.platformFeeRate)}`}
            >
              − {won(settlement.platformFee)}
            </DataRow>
            <TotalRow label="지급액">{won(settlement.netAmount)}</TotalRow>

            {error !== null && (
              <p className="ai-hint fail mt16" role="alert">
                {toMessage(error)}
              </p>
            )}

            {/*
              이체가 실패해도 서버는 200 을 돌려줍니다. 실패는 오류가 아니라
              "재처리 대상" 이라는 상태이기 때문입니다. 그래서 실행 결과는
              오류 상자가 아니라 위의 이체 상태로 확인해야 합니다.
            */}
            {(execute.isSuccess || retry.isSuccess) && (
              <p className="small muted mt16" style={{ marginBottom: 0 }}>
                실행 요청을 보냈습니다. 결과는 위의 이체 상태로 확인하세요 — 이체가 실패해도
                요청 자체는 성공으로 돌아옵니다.
              </p>
            )}

            {pending === 'hold' ? (
              <div className="mt16">
                <TextAreaField
                  label="보류 사유"
                  required
                  rows={3}
                  placeholder="왜 지급을 막는지 적어주세요. 판매자 문의와 보류 해제 판단의 근거가 됩니다."
                  value={holdReason}
                  onChange={(event) => {
                    setHoldReason(event.target.value)
                  }}
                  hint="최대 255자"
                  maxLength={255}
                />
                <div className="order-actions">
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setPending(null)
                    }}
                  >
                    취소
                  </Button>
                  <Button
                    disabled={holdReason.trim() === '' || busy}
                    onClick={() => {
                      hold.mutate()
                    }}
                  >
                    {hold.isPending ? '처리 중…' : '보류 확정'}
                  </Button>
                </div>
              </div>
            ) : pending === 'execute' || pending === 'retry' ? (
              <div className="danger mt16">
                <div className="danger-head">실제로 돈이 나갑니다</div>
                <p>
                  {won(settlement.netAmount)}을 판매자 #{settlement.sellerId} 의 정산 계좌로
                  이체합니다. 이체가 끝나면 취소할 수 없습니다.
                </p>
                <div className="order-actions">
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setPending(null)
                    }}
                  >
                    취소
                  </Button>
                  <Button
                    disabled={busy}
                    onClick={() => {
                      if (pending === 'execute') execute.mutate()
                      else retry.mutate()
                    }}
                  >
                    {busy ? '처리 중…' : '이체 실행'}
                  </Button>
                </div>
              </div>
            ) : pending === 'release' ? (
              <div className="danger mt16">
                <div className="danger-head">보류를 풉니다</div>
                <p>
                  다시 실행 대기 상태가 됩니다. 분쟁이 정리되지 않았다면 그대로 두는 편이
                  안전합니다.
                </p>
                <div className="order-actions">
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setPending(null)
                    }}
                  >
                    취소
                  </Button>
                  <Button
                    disabled={busy}
                    onClick={() => {
                      release.mutate()
                    }}
                  >
                    {release.isPending ? '처리 중…' : '보류 해제'}
                  </Button>
                </div>
              </div>
            ) : (
              <div className="order-actions">
                {canExecute && (
                  <Button
                    onClick={() => {
                      setPending('execute')
                    }}
                  >
                    이체 실행
                  </Button>
                )}
                {canRetry && (
                  <Button
                    onClick={() => {
                      setPending('retry')
                    }}
                  >
                    재처리
                  </Button>
                )}
                {canHold && (
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setPending('hold')
                    }}
                  >
                    보류
                  </Button>
                )}
                {canRelease && (
                  <Button
                    onClick={() => {
                      setPending('release')
                    }}
                  >
                    보류 해제
                  </Button>
                )}
                {settlement.status === 'COMPLETED' && (
                  <span className="small muted">지급이 끝난 건입니다.</span>
                )}
              </div>
            )}

            {settlement.transferStatus === 'FAILED' && (
              <p className="small muted mt16" style={{ marginBottom: 0 }}>
                결과를 알 수 없는 건은 그대로 재실행하면 안 됩니다. 금융망 거래내역을 먼저
                확인하세요 — 이미 이체됐는데 다시 보내면 이중 지급입니다.
              </p>
            )}
          </Panel>
        )
      }}
    </QueryBoundary>
  )
}
