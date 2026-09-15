import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { adminApi } from '@/entities/admin/api'
import { adminQueries } from '@/entities/admin/queries'
import { RETURN_REASON_LABEL, RETURN_STATUS_DISPLAY, type ReturnStatus } from '@/entities/model'
import type { ReturnRequestSummary } from '@/entities/returnRequest/model'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { date, dateTime, groupBuyNo, maskName, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { usePageParam } from '@/shared/lib/hooks/usePageParam'
import { Button } from '@/shared/ui/Button'
import { ChipFilter } from '@/shared/ui/Chips'
import { DataRow } from '@/shared/ui/DataRow'
import { TextAreaField } from '@/shared/ui/Field'
import { Pagination } from '@/shared/ui/Pagination'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

type Filter = 'ALL' | Extract<ReturnStatus, 'REQUESTED' | 'DISPUTED'>

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'REQUESTED', label: '확정 후 접수' },
  { value: 'DISPUTED', label: '이의제기' },
]

/**
 * 관리자 반품 검토.
 *
 * 판매자가 처리하는 일반 반품과 다릅니다. 여기 올라오는 건은 두 가지입니다 —
 * 구매확정 후 접수돼 에스크로가 이미 풀린 건, 그리고 판매자가 거부해 구매자가
 * 이의를 제기한 건. 둘 다 판매자에게 맡길 수 없어 플랫폼이 판단합니다.
 *
 * 승인은 사유 없이도 되지만 기각에는 사유가 필수입니다(백엔드에서 400).
 * 구매자가 왜 거절됐는지 알아야 다음 절차를 밟을 수 있기 때문입니다.
 */
export default function AdminReturnsPage() {
  useDocumentTitle('반품 검토')

  const { page, goToPage } = usePageParam()
  const [filter, setFilter] = useState<Filter>('ALL')

  const query = useQuery(
    adminQueries.returnReviews({
      page,
      size: 20,
      ...(filter === 'ALL' ? {} : { status: filter }),
    }),
  )

  const changeFilter = (next: Filter) => {
    setFilter(next)
    goToPage(0)
  }

  const items = query.data?.items ?? []
  const disputed = items.filter((item) => item.status === 'DISPUTED')
  const postConfirm = items.filter((item) => item.postConfirm)
  const sum = (list: readonly ReturnRequestSummary[]) =>
    list.reduce((total, item) => total + item.refundAmount, 0)

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">반품 검토</h2>
          <p className="sec-sub">
            판매자가 처리할 수 없는 반품을 플랫폼이 판단합니다. 구매확정 후 접수 건과 이의제기
            건이 올라옵니다.
          </p>
        </div>
      </div>

      <ChipFilter label="검토 대상" value={filter} onChange={changeFilter} options={FILTERS} />

      <div className="split">
        <div>
          <QueryBoundary query={query} loadingLabel="검토 목록을 불러오는 중입니다.">
            {(result) =>
              result.items.length === 0 ? (
                <EmptyState>검토할 반품 건이 없습니다.</EmptyState>
              ) : (
                <>
                  {result.items.map((item, index) => (
                    <ReviewCard
                      key={item.id}
                      item={item}
                      className={index > 0 ? 'mt16' : undefined}
                    />
                  ))}
                  <Pagination meta={result.meta} onChange={goToPage} label="검토 목록 페이지" />
                </>
              )
            }
          </QueryBoundary>
        </div>

        <aside>
          <Panel title="이 쪽 요약" description="지금 보고 있는 페이지 기준">
            <div className="amount">{items.length}건</div>
            <DataRow label="이의제기">
              {disputed.length}건 · {won(sum(disputed))}
            </DataRow>
            <DataRow label="구매확정 후 접수">
              {postConfirm.length}건 · {won(sum(postConfirm))}
            </DataRow>
            <DataRow label="환불 예정 합계">{won(sum(items))}</DataRow>
          </Panel>

          <Panel className="mt16" title="판단 기준" description="공구한 반품 정책">
            <DataRow label="확정 전 반품">에스크로에서 직접 환불 · 정산 제외</DataRow>
            <DataRow label="확정 후 반품">에스크로가 풀린 뒤라 관리자 검토 필수</DataRow>
            <DataRow label="단순변심">최초 배송비 미환불 · 반품비 구매자 부담</DataRow>
            <DataRow label="하자·오배송">전액 환불 · 반품비 판매자 부담</DataRow>
            <DataRow label="부당 거부">판매자 제재 후보로 올라갑니다</DataRow>
            <DataRow label="분담금 재산정">없음</DataRow>
          </Panel>
        </aside>
      </div>
    </section>
  )
}

function ReviewCard({
  item,
  className,
}: {
  item: ReturnRequestSummary
  className?: string | undefined
}) {
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<'approve' | 'reject' | null>(null)
  const [reason, setReason] = useState('')

  const display = RETURN_STATUS_DISPLAY[item.status]
  // 백엔드는 검토 대상이면서 REQUESTED·DISPUTED 인 건만 처리를 허용합니다.
  const resolvable =
    item.adminReviewRequired && (item.status === 'REQUESTED' || item.status === 'DISPUTED')

  const resolve = useMutation({
    mutationFn: (decision: 'APPROVE' | 'REJECT') =>
      adminApi.resolveReturn(item.id, decision, reason.trim() === '' ? null : reason.trim()),
    onSuccess: async () => {
      setMode(null)
      setReason('')
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.all })
    },
  })

  return (
    <Panel
      className={className}
      title={`${maskName(item.buyerName)} · ${RETURN_REASON_LABEL[item.reasonType]}`}
      description={`#${item.id} · ${item.productName} ${groupBuyNo(item.groupBuyId)} · ${date(item.requestedAt)} 신청`}
      aside={
        <div className="order-amount">
          {won(item.refundAmount)}
          <span className="small muted">환불 예정 금액</span>
        </div>
      }
    >
      <div className="box rows">
        <DataRow label="상태">
          <StateChip tone={display.tone}>{display.label}</StateChip>
        </DataRow>
        <DataRow label="검토 사유">
          {item.status === 'DISPUTED'
            ? '판매자가 거부해 구매자가 이의를 제기했습니다.'
            : '구매확정 후 접수되어 에스크로가 이미 풀렸습니다.'}
        </DataRow>
        <DataRow label="반품 배송비">
          {won(item.returnShippingFee)} ·{' '}
          {item.shippingFeeBearer === 'BUYER' ? '구매자 부담' : '판매자 부담'}
        </DataRow>
        <DataRow label="신청 시각">{dateTime(item.requestedAt)}</DataRow>
        <DataRow label="구매자 사유">{item.detailReason}</DataRow>
      </div>

      {resolve.isError && (
        <p className="ai-hint fail" role="alert">
          {toMessage(resolve.error)}
        </p>
      )}

      {!resolvable && (
        <p className="small muted mt16" style={{ marginBottom: 0 }}>
          이미 처리가 끝난 건입니다. 상태만 확인할 수 있습니다.
        </p>
      )}

      {resolvable && mode !== null && (
        <div className="mt16">
          <TextAreaField
            label={mode === 'reject' ? '기각 사유' : '승인 메모'}
            required={mode === 'reject'}
            rows={3}
            maxLength={255}
            placeholder={
              mode === 'reject'
                ? '구매자에게 전달할 기각 사유를 적어주세요. 사유 없이는 기각할 수 없습니다.'
                : '남길 말이 있으면 적어주세요. 비워두면 기본 문구가 기록됩니다.'
            }
            hint="최대 255자 · 반품 이력에 그대로 남습니다"
            value={reason}
            onChange={(event) => {
              setReason(event.target.value)
            }}
          />
          <div className="order-actions">
            <Button
              variant="ghost"
              onClick={() => {
                setMode(null)
              }}
            >
              취소
            </Button>
            <Button
              disabled={(mode === 'reject' && reason.trim() === '') || resolve.isPending}
              onClick={() => {
                resolve.mutate(mode === 'reject' ? 'REJECT' : 'APPROVE')
              }}
            >
              {resolve.isPending ? '처리 중…' : mode === 'reject' ? '기각 확정' : '승인 확정'}
            </Button>
          </div>
        </div>
      )}

      {resolvable && mode === null && (
        <>
          <div className="order-actions">
            <Button
              onClick={() => {
                setMode('approve')
              }}
            >
              반품 승인
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                setMode('reject')
              }}
            >
              사유 밝히고 기각
            </Button>
          </div>
          <p className="small muted mt16" style={{ marginBottom: 0 }}>
            승인하면 환불 절차가 시작됩니다. 구매확정 후 건이라면 이미 판매자 몫으로 잡힌
            금액을 되돌리는 것이므로, 정산이 실행되기 전인지 함께 확인하세요.
          </p>
        </>
      )}
    </Panel>
  )
}
