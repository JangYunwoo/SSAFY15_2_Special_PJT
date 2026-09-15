import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { adminApi } from '@/entities/admin/api'
import { restrictionReviewDisplay, type RestrictionProduct } from '@/entities/admin/model'
import { adminQueries } from '@/entities/admin/queries'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { dateTime, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

/**
 * 청약철회 제한 심사.
 *
 * 판매자가 "이 상품은 단순변심 반품이 안 된다" 고 걸어 올린 상품을 봅니다.
 * 승인하면 구매자의 청약철회권이 실제로 제한되므로, 근거 자료 없이 승인하면
 * 안 됩니다 — 전자상거래법이 정한 예외에 해당하는지가 판단 기준입니다.
 *
 * 목록은 페이지가 없습니다(`GET /admin/products/withdrawal-restrictions` 가
 * 전체를 한 번에 돌려줍니다). 심사 대기가 수백 건씩 쌓이는 화면이 아니라
 * 그대로 두었고, 쌓이기 시작하면 백엔드에 페이지를 요청해야 합니다.
 */
export default function AdminProductsPage() {
  useDocumentTitle('상품 심사')

  const query = useQuery(adminQueries.restrictionReviews())

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">청약철회 제한 심사</h2>
          <p className="sec-sub">
            승인하면 구매자가 그 상품을 단순변심으로 반품할 수 없습니다. 근거를 확인하고
            판단하세요.
          </p>
        </div>
      </div>

      <div className="split">
        <div>
          <QueryBoundary query={query} loadingLabel="심사 대기 상품을 불러오는 중입니다.">
            {(items) =>
              items.length === 0 ? (
                <EmptyState>심사할 상품이 없습니다.</EmptyState>
              ) : (
                <>
                  {items.map((item, index) => (
                    <ReviewCard
                      key={item.productId}
                      item={item}
                      className={index > 0 ? 'mt16' : undefined}
                    />
                  ))}
                </>
              )
            }
          </QueryBoundary>
        </div>

        <aside>
          <Panel title="심사 기준" description="전자상거래법 제17조 제2항">
            <div className="guide">
              <div className="guide-head">제한이 인정되는 경우</div>
              <ul>
                <li>구매자 책임으로 상품이 훼손된 경우</li>
                <li>사용·소비로 상품 가치가 크게 줄어든 경우</li>
                <li>시간이 지나 다시 팔기 어려워진 경우</li>
                <li>복제 가능한 상품의 포장을 훼손한 경우</li>
                <li>주문에 따라 개별 제작되어 판매자 피해가 큰 경우</li>
              </ul>
            </div>

            <div className="notice">
              <div className="notice-head">주의</div>
              <ul>
                <li>근거 자료가 없으면 반려합니다. 승인의 책임은 플랫폼에 있습니다.</li>
                <li>승인 후에도 하자·오배송 반품은 그대로 가능합니다.</li>
                <li>판매자가 제한을 남용하면 제재 후보로 올릴 수 있습니다.</li>
              </ul>
            </div>
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
  item: RestrictionProduct
  className?: string | undefined
}) {
  const queryClient = useQueryClient()
  const [pending, setPending] = useState<'APPROVED' | 'REJECTED' | null>(null)

  const status = restrictionReviewDisplay(item.restrictionReviewStatus)
  const reviewable = item.restrictionReviewStatus === 'PENDING'
  const hasEvidence = item.restrictionEvidenceUrl !== null && item.restrictionEvidenceUrl !== ''

  const review = useMutation({
    mutationFn: (decision: 'APPROVED' | 'REJECTED') =>
      adminApi.reviewRestriction(item.productId, decision),
    onSuccess: async () => {
      setPending(null)
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.all })
    },
  })

  return (
    <Panel
      className={className}
      title={item.name}
      description={`상품 #${item.productId} · 판매자 #${item.sellerId} · ${item.categoryName}`}
      aside={
        <div className="order-amount">
          {won(item.price)}
          <span className="small muted">공동구매 1회분</span>
        </div>
      }
    >
      <div className="box rows">
        <DataRow label="심사 상태">
          <StateChip tone={status.tone}>{status.label}</StateChip>
        </DataRow>
        <DataRow label="제한 사유">{item.withdrawalRestrictionReason ?? '-'}</DataRow>
        <DataRow label="근거 자료">
          {hasEvidence ? (
            <a
              className="tlink"
              href={item.restrictionEvidenceUrl ?? undefined}
              target="_blank"
              rel="noreferrer noopener"
            >
              첨부 자료 열기
            </a>
          ) : (
            '없음'
          )}
        </DataRow>
        <DataRow label="등록 시각">{dateTime(item.createdAt)}</DataRow>
      </div>

      {!hasEvidence && reviewable && (
        <p className="ai-hint fail">
          근거 자료가 없습니다. 자료 없이 승인하면 제한의 책임을 플랫폼이 집니다.
        </p>
      )}

      {review.isError && (
        <p className="ai-hint fail mt16" role="alert">
          {toMessage(review.error)}
        </p>
      )}

      {!reviewable && (
        <p className="small muted mt16" style={{ marginBottom: 0 }}>
          심사가 끝난 상품입니다.
        </p>
      )}

      {reviewable && pending !== null && (
        <div className="danger mt16">
          <div className="danger-head">
            {pending === 'APPROVED' ? '청약철회를 제한합니다' : '제한을 반려합니다'}
          </div>
          <p>
            {pending === 'APPROVED'
              ? '이 상품을 산 구매자는 단순변심으로 반품할 수 없게 됩니다. 하자·오배송 반품은 그대로 가능합니다.'
              : '판매자가 건 제한이 풀리고, 구매자는 기간 안에 단순변심 반품을 할 수 있습니다.'}
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
              disabled={review.isPending}
              onClick={() => {
                review.mutate(pending)
              }}
            >
              {review.isPending ? '처리 중…' : pending === 'APPROVED' ? '승인 확정' : '반려 확정'}
            </Button>
          </div>
        </div>
      )}

      {reviewable && pending === null && (
        <div className="order-actions">
          <Button
            onClick={() => {
              setPending('APPROVED')
            }}
          >
            제한 승인
          </Button>
          <Button
            variant="ghost"
            onClick={() => {
              setPending('REJECTED')
            }}
          >
            반려
          </Button>
        </div>
      )}
    </Panel>
  )
}
