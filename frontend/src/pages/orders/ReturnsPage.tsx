import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { RETURN_REASON_LABEL, RETURN_STATUS_DISPLAY } from '@/entities/model'
import { returnApi } from '@/entities/returnRequest/api'
import type { ReturnRequestSummary } from '@/entities/returnRequest/model'
import { returnQueries } from '@/entities/returnRequest/queries'
import { OrdersTabs } from '@/features/orders/OrdersTabs'
import { toMessage } from '@/shared/api/errors'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { date, dateTime, dday, groupBuyNo, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { TextAreaField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

/**
 * 구매자 반품·환불 내역 (RTN-03 · RTN-04).
 *
 * 반품은 신청하고 끝이 아니라 판매자 승인 → 수거 → 환불로 이어지고, 거부되면
 * 관리자에게 이의를 제기할 수 있습니다(RTN-09). 신청 화면만 있으면 구매자가
 * 자기 건이 어디까지 갔는지 확인할 방법이 없어 이 목록을 둡니다.
 */
export default function ReturnsPage() {
  useDocumentTitle('반품·환불')

  const query = useQuery(returnQueries.buyerList({ size: 100 }))

  return (
    <main className="wrap">
      <section className="section">
        <div className="sec-head">
          <div>
            <h2 className="sec-title">반품·환불</h2>
            <p className="sec-sub">신청한 반품의 처리 상태와 환불 진행을 확인합니다.</p>
          </div>
        </div>

        <OrdersTabs />

        <QueryBoundary query={query}>{(page) => <ReturnsView items={page.items} />}</QueryBoundary>
      </section>
    </main>
  )
}

function ReturnsView({ items }: { items: readonly ReturnRequestSummary[] }) {
  if (items.length === 0) {
    return <EmptyState>신청한 반품이 없습니다.</EmptyState>
  }

  const inProgress = items.filter(
    (item) => item.status !== 'REFUNDED' && item.status !== 'REJECTED',
  )
  const refunded = items.filter((item) => item.status === 'REFUNDED')
  const rejected = items.filter((item) => item.status === 'REJECTED')
  const sum = (list: readonly ReturnRequestSummary[]) =>
    list.reduce((total, item) => total + item.refundAmount, 0)

  return (
    <div className="split">
      <div>
        {items.map((item, index) => (
          <ReturnCard key={item.id} item={item} className={index > 0 ? 'mt16' : ''} />
        ))}
      </div>

      <aside>
        <Panel title="반품 현황" description="환불 예정 금액">
          <div className="amount">{won(sum(inProgress))}</div>
          <DataRow label="처리 중">
            {inProgress.length}건 · {won(sum(inProgress))}
          </DataRow>
          <DataRow label="환불 완료">
            {refunded.length}건 · {won(sum(refunded))}
          </DataRow>
          <DataRow label="거부됨">{rejected.length}건</DataRow>
        </Panel>

        <Panel className="mt16" title="반품 처리 기준" description="공구한 정책">
          <DataRow label="판매자 승인 기한">
            {POLICY_DEFAULTS.returnApprovalBusinessDays}영업일 · 무응답 시 자동 승인
          </DataRow>
          <DataRow label="단순변심 반품비">구매자 부담</DataRow>
          <DataRow label="하자·오배송 반품비">판매자 부담</DataRow>
          <DataRow label="환불 시점">판매자 수거 확인 후 개시</DataRow>
          <DataRow label="거부 시">관리자에게 이의제기 가능</DataRow>
        </Panel>
      </aside>
    </div>
  )
}

function ReturnCard({
  item,
  className,
}: {
  item: ReturnRequestSummary
  className?: string | undefined
}) {
  const [open, setOpen] = useState(false)
  const display = RETURN_STATUS_DISPLAY[item.status]

  return (
    <Panel
      className={className}
      title={item.productName}
      description={`${groupBuyNo(item.groupBuyId)} · ${date(item.requestedAt)} 신청 · ${RETURN_REASON_LABEL[item.reasonType]}`}
      aside={
        <div className="order-amount">
          {won(item.refundAmount)}
          <span className="small muted">
            {item.status === 'REFUNDED' ? '환불 완료' : '환불 예정 금액'}
          </span>
        </div>
      }
    >
      <div className="box rows">
        <DataRow label="상태">
          <StateChip tone={display.tone}>{display.label}</StateChip>
          {item.status === 'REQUESTED' && item.autoApproveAt !== null && (
            <span className="small muted"> 자동 승인까지 {dday(item.autoApproveAt)}</span>
          )}
        </DataRow>
        <DataRow label="반품 배송비">
          {won(item.returnShippingFee)} ·{' '}
          {item.shippingFeeBearer === 'BUYER' ? '구매자 부담' : '판매자 부담'}
        </DataRow>
        <DataRow label="신청 사유">{item.detailReason}</DataRow>
        {item.postConfirm && (
          <DataRow label="구매확정 후 신청">
            {item.adminReviewRequired ? '관리자 검토 대상' : '접수됨'}
          </DataRow>
        )}
      </div>

      <div className="order-actions">
        <Button
          variant="ghost"
          aria-expanded={open}
          onClick={() => {
            setOpen((current) => !current)
          }}
        >
          {open ? '처리 내역 접기' : '처리 내역 보기'}
        </Button>
      </div>

      {open && <ReturnDetail returnId={item.id} />}
    </Panel>
  )
}

/** RTN-04. 수거지·거부 사유·상태 이력은 상세 응답에만 있습니다. */
function ReturnDetail({ returnId }: { returnId: string }) {
  const query = useQuery(returnQueries.buyerDetail(returnId))

  return (
    <div className="mt16">
      <QueryBoundary query={query}>
        {(detail) => (
          <>
            <div className="box rows">
              <DataRow label="수거지">
                ({detail.pickupZipcode}) {detail.pickupAddress1} {detail.pickupAddress2}
              </DataRow>
              {detail.rejectReason !== null && (
                <DataRow label="거부 사유">{detail.rejectReason}</DataRow>
              )}
            </div>

            {detail.history.length > 0 && (
              <div className="box rows mt16">
                {detail.history.map((entry) => (
                  <DataRow
                    key={`${entry.status}-${entry.occurredAt}`}
                    label={RETURN_STATUS_DISPLAY[entry.status].label}
                    hint={entry.note ?? undefined}
                  >
                    <span className="small muted">{dateTime(entry.occurredAt)}</span>
                  </DataRow>
                ))}
              </div>
            )}

            {detail.status === 'REJECTED' && <DisputeForm returnId={returnId} />}
          </>
        )}
      </QueryBoundary>
    </div>
  )
}

/** RTN-09 반품 거부 이의제기. 관리자가 심사해 강제 승인·기각합니다. */
function DisputeForm({ returnId }: { returnId: string }) {
  const queryClient = useQueryClient()
  const [detailReason, setDetailReason] = useState('')

  const dispute = useMutation({
    mutationFn: () => returnApi.dispute(returnId, detailReason),
    onSuccess: async () => {
      setDetailReason('')
      await queryClient.invalidateQueries({ queryKey: ['returns'] })
    },
  })

  if (dispute.isSuccess) {
    return (
      <p className="small muted mt16" style={{ marginBottom: 0 }}>
        이의제기를 접수했습니다. 관리자 심사 결과가 나올 때까지 구매확정이 보류됩니다.
      </p>
    )
  }

  return (
    <div className="mt16">
      <TextAreaField
        label="이의제기 사유"
        required
        hint="상품 상태와 거부 사유가 다른 점을 구체적으로 적어주세요. 관리자가 심사합니다."
        placeholder="예) 수령 당시부터 화면에 흠집이 있었고 사용한 적이 없습니다."
        value={detailReason}
        onChange={(event) => {
          setDetailReason(event.target.value)
        }}
      />

      {dispute.isError && (
        <p className="ai-hint fail mt8" role="alert">
          {toMessage(dispute.error)}
        </p>
      )}

      <div className="order-actions">
        <Button
          disabled={detailReason.trim() === '' || dispute.isPending}
          onClick={() => {
            dispute.mutate()
          }}
        >
          {dispute.isPending ? '접수 중…' : '관리자에게 이의제기'}
        </Button>
      </div>
    </div>
  )
}
