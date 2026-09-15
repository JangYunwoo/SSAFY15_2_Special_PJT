import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import { GROUP_BUY_STATUS_DISPLAY } from '@/entities/model'
import { groupBuyApi } from '@/entities/groupBuy/api'
import type { Participant, SellerGroupBuyDetail } from '@/entities/groupBuy/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { date, dateTime, groupBuyNo, maskName, won } from '@/shared/lib/format'
import { usePageParam } from '@/shared/lib/hooks/usePageParam'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button, ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { TextField } from '@/shared/ui/Field'
import { Pagination } from '@/shared/ui/Pagination'
import { Panel } from '@/shared/ui/Panel'
import { ProgressBar } from '@/shared/ui/ProgressBar'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

export default function SellerGroupBuyDetailPage() {
  useDocumentTitle('공동구매 상세')

  const { groupBuyId = '' } = useParams()
  const { page, goToPage } = usePageParam()

  const detail = useQuery(groupBuyQueries.sellerDetail(groupBuyId))

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

      <QueryBoundary query={detail}>
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
              <EscrowPanel groupBuyId={groupBuyId} />
            </aside>
          </div>
        )}
      </QueryBoundary>
    </section>
  )
}

function SummaryPanel({ item }: { item: SellerGroupBuyDetail }) {
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
      <DataRow label="에스크로 보관액">{won(item.escrowHeldAmount)}</DataRow>
      <DataRow label="예상 정산액">{won(item.estimatedSettlementAmount)}</DataRow>
      <DataRow label="반품 접수">{item.returnCount}건</DataRow>

      <ButtonLink variant="ghost" block className="mt16" to="/seller/orders">
        주문 관리로 이동
      </ButtonLink>

      {/* canEdit·canCancel 은 서버가 판단해 내려줍니다(SellerGroupBuyDetailResponse).
          참여자나 진행 중인 결제가 있으면 둘 다 거짓이 되므로 버튼을 감춥니다. */}
      {item.canEdit && (
        <ButtonLink variant="ghost" block className="mt8" to={`/seller/group-buys/${item.id}/edit`}>
          모집 조건 수정
        </ButtonLink>
      )}
      {item.canCancel && <CancelGroupBuy item={item} />}
    </Panel>
  )
}

/**
 * GRP-08 공동구매 취소.
 *
 * 되돌릴 수 없어서 사유를 적게 한 뒤에야 버튼이 열립니다. 백엔드도 빈 사유를
 * 거절합니다(GroupBuyTerminationRequest 의 @NotBlank).
 */
function CancelGroupBuy({ item }: { item: SellerGroupBuyDetail }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [asking, setAsking] = useState(false)
  const [reason, setReason] = useState('')

  const cancel = useMutation({
    mutationFn: () => groupBuyApi.cancel(item.id, reason.trim()),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.sellerGroupBuys.all })
      await navigate('/seller', { replace: true })
    },
  })

  if (!asking) {
    return (
      <Button
        variant="ghost"
        block
        className="mt8"
        onClick={() => {
          setAsking(true)
        }}
      >
        공동구매 취소
      </Button>
    )
  }

  return (
    <div className="mt16">
      <TextField
        label="취소 사유"
        required
        value={reason}
        maxLength={500}
        onChange={(event) => {
          setReason(event.target.value)
        }}
        hint="판매자에게만 보이는 기록입니다. 취소한 공동구매는 되돌릴 수 없습니다."
      />

      {cancel.isError && <p className="ai-hint fail">{toMessage(cancel.error)}</p>}

      <Button
        block
        className="mt8"
        disabled={reason.trim() === '' || cancel.isPending}
        onClick={() => {
          cancel.mutate()
        }}
      >
        {cancel.isPending ? '취소하는 중…' : '취소 확정'}
      </Button>
      <Button
        variant="ghost"
        block
        className="mt8"
        disabled={cancel.isPending}
        onClick={() => {
          setAsking(false)
          setReason('')
        }}
      >
        그대로 두기
      </Button>
    </div>
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

/**
 * ESC-02 이 공동구매의 에스크로 집계.
 * 보관 중인 돈과 이미 풀린 돈을 나눠 보여줍니다.
 */
function EscrowPanel({ groupBuyId }: { groupBuyId: string }) {
  const escrows = useQuery(groupBuyQueries.escrows(groupBuyId))

  return (
    <Panel className="mt16" title="에스크로" description="구매확정 전까지 플랫폼이 보관합니다">
      <QueryBoundary query={escrows}>
        {(result) => (
          <>
            <DataRow label="보관 중">
              {result.summary.heldCount}건 · {won(result.summary.heldAmount)}
            </DataRow>
            <DataRow label="해제 (정산 대상)">
              {result.summary.releasedCount}건 · {won(result.summary.releasedAmount)}
            </DataRow>
            <DataRow label="환불">
              {result.summary.refundedCount}건 · {won(result.summary.refundedAmount)}
            </DataRow>
            <DataRow label="합계">
              {result.summary.totalCount}건 · {won(result.summary.totalAmount)}
            </DataRow>
          </>
        )}
      </QueryBoundary>

      <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
        보관 중인 금액은 구매확정 전이라 아직 정산 대상이 아닙니다. 배송완료 후 7일이 지나면
        자동으로 확정됩니다.
      </p>
    </Panel>
  )
}
