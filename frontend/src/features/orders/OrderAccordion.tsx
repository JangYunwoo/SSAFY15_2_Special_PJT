import { useId, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'

import { PARTICIPATION_STATUS_DISPLAY, type EscrowStatus } from '@/entities/model'
import { participationApi } from '@/entities/participation/api'
import type { Escrow, ParticipationSummary } from '@/entities/participation/model'
import { participationQueries } from '@/entities/participation/queries'
import type { IsoDateTime } from '@/shared/api/types'
import { queryKeys } from '@/shared/api/queryKeys'
import { toMessage } from '@/shared/api/errors'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'
import { cx } from '@/shared/lib/cx'
import { date, dateTime, dday, groupBuyNo, number, won } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { StateChip } from '@/shared/ui/StateChip'
import { Timeline } from '@/shared/ui/Timeline'

import { participationTimeline } from './participationTimeline'

interface OrderAccordionProps {
  item: ParticipationSummary
  /** 기본 펼침 여부. 진행 중인 건은 펼쳐서 보여줍니다. */
  defaultOpen?: boolean | undefined
}

export function OrderAccordion({ item, defaultOpen = false }: OrderAccordionProps) {
  const [open, setOpen] = useState(defaultOpen)
  const panelId = useId()
  const queryClient = useQueryClient()

  const display = PARTICIPATION_STATUS_DISPLAY[item.status]

  const cancel = useMutation({
    mutationFn: () => participationApi.cancel(item.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.participations.all })
    },
  })

  const statusLabel =
    item.status === 'DELIVERED' && item.autoConfirmAt !== null
      ? `${display.label} ${dday(item.autoConfirmAt)}`
      : display.label

  return (
    <div className={cx('order', open && 'open')}>
      <button
        type="button"
        className="order-row"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => {
          setOpen((current) => !current)
        }}
      >
        <span className="info">
          <span className="name">{item.productName}</span>
          <span className="small muted">
            공동구매 {groupBuyNo(item.groupBuyId)} · {item.sellerName} · {date(item.paidAt)} 결제
          </span>
        </span>
        <StateChip tone={display.tone}>{statusLabel}</StateChip>
        <span className="amount-s">{won(item.totalAmount)}</span>
        <span className="caret" aria-hidden="true" />
      </button>

      <div className="order-detail" id={panelId} hidden={!open}>
        <Timeline steps={participationTimeline(item.status)} />

        <div className="box rows mt24">
          <DataRow label="결제 금액">
            {won(item.totalAmount)}{' '}
            <span className="small muted">
              분담금 {number(item.shareAmount)} + 배송비 {number(item.shippingFee)}
            </span>
          </DataRow>

          {item.status === 'PAID' && (
            <>
              <DataRow label="현재 상태">
                {display.label} · 모집 {item.paidCount} / {item.targetCount}명
              </DataRow>
              <DataRow label="모집 마감">{date(item.recruitEndAt)}</DataRow>
              <DataRow label="참여 취소">모집 종료 전까지 가능</DataRow>
            </>
          )}

          {item.deliveredAt !== null && (
            <DataRow label="배송 완료">{date(item.deliveredAt)}</DataRow>
          )}

          {item.status === 'DELIVERED' && (
            <>
              <DataRow label="자동 구매확정">
                {date(item.autoConfirmAt)} · {dday(item.autoConfirmAt)}
              </DataRow>
              <DataRow label="청약철회 가능">
                {date(item.withdrawalDeadlineAt)}까지 · {dday(item.withdrawalDeadlineAt)}
              </DataRow>
            </>
          )}

          {item.confirmedAt !== null && (
            <>
              <DataRow label="구매확정">
                {date(item.confirmedAt)} · {item.autoConfirmed ? '자동 확정' : '직접 확정'}
              </DataRow>
              <EscrowRow participationId={item.id} />
            </>
          )}
        </div>

        {cancel.isError && (
          <p className="ai-hint" role="alert">
            {toMessage(cancel.error)}
          </p>
        )}

        {/*
          PAR-03(참여 취소)은 백엔드에 컨트롤러가 아직 없어 눌러도 404 입니다.
          되지 않는 버튼을 보여주는 대신 감추고, 엔드포인트가 생기면
          backendFeatures.participationCancel 만 켜면 됩니다.
        */}
        {item.status === 'PAID' && BACKEND_FEATURES.participationCancel && (
          <div className="order-actions">
            <Button
              variant="ghost"
              disabled={cancel.isPending}
              onClick={() => {
                cancel.mutate()
              }}
            >
              {cancel.isPending ? '취소 처리 중…' : '참여 취소하기'}
            </Button>
          </div>
        )}

        {item.status === 'DELIVERED' && (
          <>
            <div className="order-actions">
              <Link className="btn" to={`/orders/${item.id}/confirm`}>
                구매확정하기
              </Link>
              <Link className="btn-ghost" to={`/orders/${item.id}/return`}>
                반품 신청
              </Link>
            </div>
            <p className="small muted mt16" style={{ marginBottom: 0 }}>
              구매확정을 하면 판매자에게 정산이 개시되며 취소할 수 없습니다. 기한까지 확정하지
              않으면 자동으로 확정됩니다.
            </p>
          </>
        )}
      </div>
    </div>
  )
}

/** 상태별 표기. 목록의 escrowStatus 대신 ESC-01 응답을 그대로 씁니다. */
const ESCROW_LABEL: Record<EscrowStatus, string> = {
  HELD: '보관중',
  RELEASED: '해제 · 판매자 정산',
  REFUNDED: '환불 완료',
}

/**
 * 어느 시각을 보여줄지 고릅니다.
 *
 * 상태에 따라 의미가 달라지므로 시각만 적으면 보관 시작인지 해제인지
 * 구분되지 않습니다. 무엇의 시각인지 이름을 함께 돌려줍니다.
 */
function escrowMoment(escrow: Escrow): { label: string; at: IsoDateTime } {
  if (escrow.refundedAt !== null) return { label: '환불', at: escrow.refundedAt }
  if (escrow.releasedAt !== null) return { label: '해제', at: escrow.releasedAt }
  return { label: '보관 시작', at: escrow.heldAt }
}

/**
 * 에스크로 보관 상태 (ESC-01).
 *
 * 펼친 참여 건만 조회합니다. 목록 응답의 escrowStatus 로도 상태는 알 수 있지만,
 * 보관 금액과 시각은 이 응답에만 있습니다.
 */
function EscrowRow({ participationId }: { participationId: string }) {
  const escrow = useQuery(participationQueries.escrow(participationId))

  if (escrow.isPending) return <DataRow label="에스크로">불러오는 중…</DataRow>
  if (escrow.isError) {
    return <DataRow label="에스크로">{toMessage(escrow.error)}</DataRow>
  }

  const moment = escrowMoment(escrow.data)

  return (
    <DataRow label="에스크로">
      {ESCROW_LABEL[escrow.data.status]} · {won(escrow.data.amount)}
      <br />
      <span className="small muted">
        {moment.label} {dateTime(moment.at)}
      </span>
    </DataRow>
  )
}
