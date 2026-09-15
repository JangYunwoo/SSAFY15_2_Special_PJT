import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { adminApi } from '@/entities/admin/api'
import {
  refundMethodLabel,
  refundReasonLabel,
  refundStatusDisplay,
  type Refund,
  type RefundStatus,
} from '@/entities/admin/model'
import { adminQueries } from '@/entities/admin/queries'
import { newIdempotencyKey } from '@/shared/api/client'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { dateTime, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { usePageParam } from '@/shared/lib/hooks/usePageParam'
import { Button } from '@/shared/ui/Button'
import { ChipFilter } from '@/shared/ui/Chips'
import { DataRow } from '@/shared/ui/DataRow'
import { Pagination } from '@/shared/ui/Pagination'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

type Filter = 'ALL' | RefundStatus

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'FAILED', label: '실패' },
  { value: 'PROCESSING', label: '진행중' },
  { value: 'COMPLETED', label: '완료' },
]

/**
 * RFD-03·04 환불 관리.
 *
 * 실패 필터를 앞에 둔 이유는 이 화면을 여는 대부분의 이유가 실패 건이기
 * 때문입니다. 환불이 실패한 채로 두면 구매자 돈이 묶입니다.
 */
export default function AdminRefundsPage() {
  useDocumentTitle('환불 관리')

  const { page, goToPage } = usePageParam()
  const [filter, setFilter] = useState<Filter>('FAILED')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const query = useQuery(
    adminQueries.refunds({ page, size: 20, ...(filter === 'ALL' ? {} : { status: filter }) }),
  )

  const changeFilter = (next: Filter) => {
    setFilter(next)
    setSelectedId(null)
    goToPage(0)
  }

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">환불 관리</h2>
          <p className="sec-sub">
            실패한 환불을 재처리합니다. 재처리는 돈을 다시 내보내는 동작이라 중복 실행을 막는
            키가 함께 나갑니다.
          </p>
        </div>
      </div>

      <ChipFilter label="환불 상태" value={filter} onChange={changeFilter} options={FILTERS} />

      <div className="split">
        <div>
          <QueryBoundary query={query} loadingLabel="환불 목록을 불러오는 중입니다.">
            {(result) =>
              result.items.length === 0 ? (
                <EmptyState>해당 상태의 환불 건이 없습니다.</EmptyState>
              ) : (
                <>
                  <div className="table-scroll">
                    <table className="list">
                      <thead>
                        <tr>
                          <th>환불 번호</th>
                          <th>상품</th>
                          <th>사유</th>
                          <th>수단</th>
                          <th>상태</th>
                          <th className="num">금액</th>
                        </tr>
                      </thead>
                      <tbody>
                        {result.items.map((item) => (
                          <RefundRow
                            key={item.refundId}
                            item={item}
                            selected={item.refundId === selectedId}
                            onSelect={() => {
                              setSelectedId(item.refundId)
                            }}
                          />
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Pagination meta={result.meta} onChange={goToPage} label="환불 목록 페이지" />
                </>
              )
            }
          </QueryBoundary>
        </div>

        <aside>
          {selectedId === null ? (
            <Panel title="환불 상세" description="목록에서 한 건을 고르면 조치할 수 있습니다">
              <EmptyState>선택된 환불 건이 없습니다.</EmptyState>
            </Panel>
          ) : (
            <RefundDetailPanel refundId={selectedId} />
          )}
        </aside>
      </div>
    </section>
  )
}

function RefundRow({
  item,
  selected,
  onSelect,
}: {
  item: Refund
  selected: boolean
  onSelect: () => void
}) {
  const status = refundStatusDisplay(item.status)

  return (
    <tr style={selected ? { background: 'var(--soft)' } : undefined}>
      <td>
        <button type="button" className="tlink" onClick={onSelect}>
          <b className="mono">#{item.refundId}</b>
        </button>
      </td>
      <td>{item.productName ?? '-'}</td>
      <td>{refundReasonLabel(item.reasonType)}</td>
      <td>{refundMethodLabel(item.refundMethod)}</td>
      <td>
        <StateChip tone={status.tone}>{status.label}</StateChip>
      </td>
      <td className="num">
        <b>{won(item.amount)}</b>
      </td>
    </tr>
  )
}

function RefundDetailPanel({ refundId }: { refundId: string }) {
  const queryClient = useQueryClient()
  const query = useQuery(adminQueries.refund(refundId))

  /**
   * 확인 화면을 열 때 키를 한 번 만들고, 그 시도 안에서는 같은 키를 씁니다.
   *
   * 호출할 때마다 새로 만들면 `Idempotency-Key` 가 있으나 마나입니다 — 응답이
   * 늦어 운영자가 다시 누르면 서버 입장에서는 다른 요청이 되어 두 번 나갑니다.
   * 키는 "이 시도" 를 가리켜야 합니다.
   */
  const [attemptKey, setAttemptKey] = useState<string | null>(null)

  const retry = useMutation({
    mutationFn: (key: string) => adminApi.retryRefund(refundId, key),
    onSuccess: async () => {
      setAttemptKey(null)
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.all })
    },
  })

  return (
    <QueryBoundary query={query} loadingLabel="환불 상세를 불러오는 중입니다.">
      {(refund) => {
        const status = refundStatusDisplay(refund.status)
        const canRetry = refund.status === 'FAILED'

        return (
          <Panel
            title={`환불 #${refund.refundId}`}
            description={refund.productName ?? '상품 정보 없음'}
            aside={
              <div className="order-amount">
                {won(refund.amount)}
                <span className="small muted">{status.label}</span>
              </div>
            }
          >
            <div className="box rows">
              <DataRow label="상태">
                <StateChip tone={status.tone}>{status.label}</StateChip>
              </DataRow>
              <DataRow label="사유">{refundReasonLabel(refund.reasonType)}</DataRow>
              <DataRow label="환불 수단">{refundMethodLabel(refund.refundMethod)}</DataRow>
              <DataRow label="참여 건">
                {refund.participationId === null ? '-' : `#${refund.participationId}`}
              </DataRow>
              <DataRow label="연결된 반품">
                {refund.returnId === null ? '-' : `#${refund.returnId}`}
              </DataRow>
            </div>

            <DataRow className="mt16" label="입금 계좌" hint="뒤 4자리만 내려옵니다">
              <span className="mono">{refund.toAccountNo ?? '-'}</span>
            </DataRow>
            <DataRow label="금융망 거래번호">
              <span className="mono">{refund.institutionTxNo ?? '-'}</span>
            </DataRow>
            <DataRow label="요청 시각">{dateTime(refund.createdAt)}</DataRow>
            <DataRow label="처리 시각">{dateTime(refund.processedAt)}</DataRow>

            {refund.toAddress !== null && (
              <>
                <DataRow className="mt16" label="코인 수령 주소">
                  <span className="mono" style={{ wordBreak: 'break-all' }}>
                    {refund.toAddress}
                  </span>
                </DataRow>
                <DataRow label="전송 수량">
                  <span className="mono">{refund.assetAmount ?? '-'}</span>
                </DataRow>
                <DataRow label="트랜잭션 해시">
                  <span className="mono" style={{ wordBreak: 'break-all' }}>
                    {refund.txHash ?? '-'}
                  </span>
                </DataRow>
              </>
            )}

            {refund.failReason !== null && (
              <p className="ai-hint fail" role="alert">
                실패 사유 · {refund.failReason}
              </p>
            )}

            {retry.isError && (
              <p className="ai-hint fail mt16" role="alert">
                {toMessage(retry.error)}
              </p>
            )}

            {attemptKey !== null ? (
              <div className="danger mt16">
                <div className="danger-head">환불을 다시 내보냅니다</div>
                <p>
                  {won(refund.amount)}을 다시 지급합니다. 이미 지급된 건을 재처리하면 이중
                  지급이 되므로, 금융망 거래내역에서 실제로 나가지 않았는지 먼저 확인하세요.
                </p>
                <div className="order-actions">
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setAttemptKey(null)
                    }}
                  >
                    취소
                  </Button>
                  <Button
                    disabled={retry.isPending}
                    onClick={() => {
                      retry.mutate(attemptKey)
                    }}
                  >
                    {retry.isPending ? '처리 중…' : '재처리 실행'}
                  </Button>
                </div>
                <p className="small muted mt8" style={{ marginBottom: 0 }}>
                  중복 실행 방지 키 <span className="mono">{attemptKey}</span>
                </p>
              </div>
            ) : (
              <div className="order-actions">
                {canRetry ? (
                  <Button
                    onClick={() => {
                      setAttemptKey(newIdempotencyKey())
                    }}
                  >
                    재처리
                  </Button>
                ) : (
                  <span className="small muted">
                    실패한 환불만 재처리할 수 있습니다.
                  </span>
                )}
              </div>
            )}
          </Panel>
        )
      }}
    </QueryBoundary>
  )
}
