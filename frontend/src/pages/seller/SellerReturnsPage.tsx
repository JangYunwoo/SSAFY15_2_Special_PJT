import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { RETURN_REASON_LABEL, RETURN_STATUS_DISPLAY, type ReturnStatus } from '@/entities/model'
import { returnApi } from '@/entities/returnRequest/api'
import type { ReturnRequestSummary } from '@/entities/returnRequest/model'
import { returnQueries } from '@/entities/returnRequest/queries'
import { toMessage } from '@/shared/api/errors'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { date, dday, groupBuyNo, maskName, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { ChipFilter } from '@/shared/ui/Chips'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { TextAreaField } from '@/shared/ui/Field'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

type Filter = 'ALL' | ReturnStatus

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'REQUESTED', label: '반품신청' },
  { value: 'APPROVED', label: '반품승인' },
  { value: 'PICKED_UP', label: '수거완료' },
  { value: 'REFUNDED', label: '환불완료' },
]

export default function SellerReturnsPage() {
  useDocumentTitle('반품 관리')

  const [filter, setFilter] = useState<Filter>('ALL')
  const query = useQuery(returnQueries.sellerList({ size: 100 }))

  const items = query.data?.items ?? []
  const visible = filter === 'ALL' ? items : items.filter((item) => item.status === filter)

  const counts = (status: Filter): number =>
    status === 'ALL' ? items.length : items.filter((item) => item.status === status).length

  const pending = items.filter((item) => item.status === 'REQUESTED')
  const inProgress = items.filter(
    (item) => item.status === 'APPROVED' || item.status === 'PICKED_UP',
  )
  const done = items.filter((item) => item.status === 'REFUNDED')
  const sum = (list: readonly ReturnRequestSummary[]) =>
    list.reduce((total, item) => total + item.refundAmount, 0)

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">반품 관리</h2>
          <p className="sec-sub">
            반품 신청을 승인하거나 사유를 밝혀 거부합니다. 영업일 기준{' '}
            {POLICY_DEFAULTS.returnApprovalBusinessDays}일 이내 미처리 시 자동 승인됩니다.
          </p>
        </div>
      </div>

      <ChipFilter
        label="반품 상태"
        value={filter}
        onChange={setFilter}
        options={FILTERS.map((item) => ({ ...item, count: counts(item.value) }))}
      />

      <div className="split">
        <div>
          <QueryBoundary query={query}>
            {() =>
              visible.length === 0 ? (
                <EmptyState>해당 상태의 반품 건이 없습니다.</EmptyState>
              ) : (
                <>
                  {visible.map((item, index) => (
                    <ReturnCard key={item.id} item={item} className={index > 0 ? 'mt16' : ''} />
                  ))}
                </>
              )
            }
          </QueryBoundary>
        </div>

        <aside>
          <Panel title="반품 현황" description="최근 30일">
            <div className="amount">{items.length}건</div>
            <DataRow label="처리 대기">
              {pending.length}건 · {won(sum(pending))}
            </DataRow>
            <DataRow label="수거·환불 진행">
              {inProgress.length}건 · {won(sum(inProgress))}
            </DataRow>
            <DataRow label="환불 완료">
              {done.length}건 · {won(sum(done))}
            </DataRow>
          </Panel>

          <Panel className="mt16" title="반품 처리 기준" description="공구한 정책">
            <DataRow label="승인 기한">
              {POLICY_DEFAULTS.returnApprovalBusinessDays}영업일 · 무응답 시 자동 승인
            </DataRow>
            <DataRow label="단순변심 반품비">구매자 부담</DataRow>
            <DataRow label="하자·오배송 반품비">판매자 부담</DataRow>
            <DataRow label="확정 전 반품">에스크로 직접 환불 · 정산 제외</DataRow>
            <DataRow label="확정 후 반품">관리자 검토 후 처리</DataRow>
            <DataRow label="분담금 재산정">없음</DataRow>
          </Panel>
        </aside>
      </div>
    </section>
  )
}

function ReturnCard({
  item,
  className,
}: {
  item: ReturnRequestSummary
  className?: string | undefined
}) {
  const queryClient = useQueryClient()
  const [rejecting, setRejecting] = useState(false)
  const [rejectReason, setRejectReason] = useState('')

  const display = RETURN_STATUS_DISPLAY[item.status]

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['returns'] })
  }

  const approve = useMutation({
    mutationFn: () => returnApi.approve(item.id),
    onSuccess: invalidate,
  })
  const reject = useMutation({
    mutationFn: () => returnApi.reject(item.id, rejectReason),
    onSuccess: async () => {
      setRejecting(false)
      await invalidate()
    },
  })
  const pickup = useMutation({
    mutationFn: () => returnApi.pickupComplete(item.id),
    onSuccess: invalidate,
  })

  const error = approve.error ?? reject.error ?? pickup.error

  return (
    <Panel
      className={className}
      title={`${maskName(item.buyerName)} · ${RETURN_REASON_LABEL[item.reasonType]}`}
      description={`${item.id} · ${item.productName} ${groupBuyNo(item.groupBuyId)} · ${date(item.requestedAt)} 신청`}
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
          {display.label}
          {item.status === 'REQUESTED' && item.autoApproveAt !== null && (
            <> · 자동 승인까지 {dday(item.autoApproveAt)}</>
          )}
        </DataRow>
        <DataRow label="구매확정 여부">
          {item.postConfirm ? '확정 후 · 관리자 검토 대상' : '확정 전 · 에스크로에서 직접 환불'}
        </DataRow>
        <DataRow label="반품 배송비">
          {won(item.returnShippingFee)} ·{' '}
          {item.shippingFeeBearer === 'BUYER' ? '구매자 부담' : '판매자 부담'}
        </DataRow>
        <DataRow label="정산 영향">이 건은 정산 대상에서 제외</DataRow>
        <DataRow label="상세 사유">{item.detailReason}</DataRow>
      </div>

      {error !== null && (
        <p className="ai-hint fail" role="alert">
          {toMessage(error)}
        </p>
      )}

      {rejecting && (
        <div className="mt16">
          <TextAreaField
            label="거부 사유"
            required
            placeholder="구매자에게 전달할 거부 사유를 적어주세요."
            value={rejectReason}
            onChange={(event) => {
              setRejectReason(event.target.value)
            }}
          />
          <div className="order-actions">
            <Button
              variant="ghost"
              onClick={() => {
                setRejecting(false)
              }}
            >
              취소
            </Button>
            <Button
              disabled={rejectReason.trim() === '' || reject.isPending}
              onClick={() => {
                reject.mutate()
              }}
            >
              {reject.isPending ? '처리 중…' : '거부 확정'}
            </Button>
          </div>
        </div>
      )}

      {!rejecting && item.status === 'REQUESTED' && (
        <>
          <div className="order-actions">
            <Button
              disabled={approve.isPending}
              onClick={() => {
                approve.mutate()
              }}
            >
              {approve.isPending ? '처리 중…' : '반품 승인'}
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                setRejecting(true)
              }}
            >
              사유 밝히고 거부
            </Button>
          </div>
          <p className="small muted mt16" style={{ marginBottom: 0 }}>
            구매자 책임으로 상품이 훼손되었거나 사용으로 가치가 크게 줄었다면 거부할 수 있습니다.
            거부 시 구매자는 관리자에게 이의를 제기할 수 있습니다.
          </p>
        </>
      )}

      {item.status === 'APPROVED' && (
        <div className="order-actions">
          <Button
            disabled={pickup.isPending}
            onClick={() => {
              pickup.mutate()
            }}
          >
            {pickup.isPending ? '처리 중…' : '수거 완료 확인'}
          </Button>
        </div>
      )}
    </Panel>
  )
}
