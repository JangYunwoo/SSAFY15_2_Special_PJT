import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'

import {
  DELIVERY_STATUS_DISPLAY,
  PARTICIPATION_STATUS_DISPLAY,
  type EscrowStatus,
  type ParticipationStatus,
} from '@/entities/model'
import { displayFor } from '@/entities/statusDisplay'
import { deliveryQueries } from '@/entities/delivery/queries'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { participationApi } from '@/entities/participation/api'
import type { Escrow, ParticipationSummary } from '@/entities/participation/model'
import { participationQueries } from '@/entities/participation/queries'
import type { IsoDateTime } from '@/shared/api/types'
import { queryKeys } from '@/shared/api/queryKeys'
import { toMessage } from '@/shared/api/errors'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'
import { date, dateTime, dday, groupBuyNo, number, won } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { TextField } from '@/shared/ui/Field'
import { StateChip } from '@/shared/ui/StateChip'
import { Timeline } from '@/shared/ui/Timeline'

import { participationTimeline } from './participationTimeline'


export function OrderDetail({ item }: { item: ParticipationSummary }) {
  /** 참여 취소 확인 단계를 보여줄지 */
  const [askingCancel, setAskingCancel] = useState(false)
  /** 카드 결제 건 취소 시 카드 승인 취소에 다시 필요한 카드 정보(저장하지 않음) */
  const [cardNumber, setCardNumber] = useState('')
  const [cvc, setCvc] = useState('')
  const queryClient = useQueryClient()

  const display = displayFor(PARTICIPATION_STATUS_DISPLAY, item.status, '참여 상태')

  /*
   * 대표 이미지는 참여 응답(PAR-01·02)에 없어 공동구매에서 가져옵니다. 목록에서는
   * 건마다 한 번씩 더 부르게 되므로 하지 않지만, 상세는 한 건짜리 화면이라
   * 한 번이면 됩니다. 못 받아도 회색 자리만 남고 나머지는 그대로 보입니다.
   */
  const groupBuy = useQuery(groupBuyQueries.detail(item.groupBuyId))
  const thumbnailUrl = groupBuy.data?.thumbnailUrl ?? null

  // 카드 결제는 SSAFY 카드 취소가 카드번호·CVC 를 다시 요구합니다. 카카오페이·코인은 필요 없습니다.
  const isCardPayment = item.method === 'CARD'
  const cardDigits = cardNumber.replace(/\D/g, '')
  const cvcDigits = cvc.replace(/\D/g, '')
  const cardReady = !isCardPayment || (/^\d{15,16}$/.test(cardDigits) && /^\d{3}$/.test(cvcDigits))

  const resetCancel = () => {
    setAskingCancel(false)
    setCardNumber('')
    setCvc('')
  }

  const cancel = useMutation({
    mutationFn: () =>
      participationApi.cancel(item.id, isCardPayment ? { cardNumber: cardDigits, cvc: cvcDigits } : undefined),
    onSuccess: async () => {
      resetCancel()
      // 취소하면 슬롯이 풀려 공동구매의 참여 인원이 줄고, 결제는 환불로 넘어갑니다.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.participations.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.groupBuys.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.payments.all }),
      ])
    },
  })

  const statusLabel =
    item.status === 'DELIVERED' && item.autoConfirmAt !== null
      ? `${display.label} ${dday(item.autoConfirmAt)}`
      : display.label

  return (
    <div className="order open">
      {/*
        제목 줄은 페이지가 그립니다. 여기는 상태와 무엇을 샀는지부터 세웁니다.
        판매자·공동구매 번호를 오른쪽 끝에 흘려 두면 카드가 넓어질수록 상태와
        멀어져, 서로 무관한 두 조각처럼 읽힙니다.
      */}
      <div className="order-state">
        <StateChip tone={display.tone}>{statusLabel}</StateChip>
      </div>

      <div className="order-item order-product">
        <div className="thumb">
          {thumbnailUrl !== null && <img src={thumbnailUrl} alt="" loading="lazy" />}
        </div>
        <div className="body">
          <p className="name">{item.productName}</p>
          <p className="small muted">
            공동구매 {groupBuyNo(item.groupBuyId)} · {item.sellerName}
          </p>
          <p className="order-product-amount">{won(item.totalAmount)}</p>
        </div>
      </div>

      <div className="order-detail">
        <Timeline steps={participationTimeline(item.status)} />

        <div className="box rows mt24">
          <DataRow label="결제 금액">
            {won(item.totalAmount)}{' '}
            <span className="small muted">
              분담금 {number(item.shareAmount)} + 배송비 {number(item.shippingFee)}
            </span>
          </DataRow>

          {/*
            결제 시각을 분 단위까지 보여줍니다 (S15P21B205-229). 접힌 줄에는
            날짜만 있어서, 같은 날 여러 건을 결제하면 어느 것이 무엇인지 구분되지
            않았습니다. 결제는 분쟁이 생겼을 때 기준이 되는 시각이라 남겨 둡니다.
          */}
          <DataRow label="결제 일시">{dateTime(item.paidAt)}</DataRow>

          {item.status === 'PAID' && (
            <>
              <DataRow label="현재 상태">
                {display.label} · 모집 {item.paidCount} / {item.targetCount}명
              </DataRow>
              <DataRow label="모집 마감">{date(item.recruitEndAt)}</DataRow>
              <DataRow label="참여 취소">모집 종료 전까지 가능</DataRow>
            </>
          )}

          {hasDelivery(item.status) && <DeliveryRow participationId={item.id} open />}

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
              <EscrowRow participationId={item.id} open />
            </>
          )}
        </div>

        {cancel.isError && (
          <p className="ai-hint" role="alert">
            {toMessage(cancel.error)}
          </p>
        )}

        {/*
          PAR-03 참여 취소. 결제 완료(PAID) 이면서 모집이 끝나지 않은 건만
          백엔드가 받아들입니다. 되돌릴 수 없고 바로 환불이 시작되므로 한 번 더
          묻습니다.
        */}
        {item.status === 'PAID' && BACKEND_FEATURES.participationCancel && (
          <div className="order-actions">
            {askingCancel ? (
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 12 }}>
                <span className="small">
                  참여를 취소하고 결제금을 환불받습니다. 되돌릴 수 없습니다.
                </span>
                {isCardPayment && (
                  <>
                    <p className="small muted" style={{ margin: 0 }}>
                      카드 결제는 카드 승인을 취소해 돌려드립니다. 보안상 카드 정보를 저장하지
                      않아, 취소를 위해 카드번호와 CVC 를 다시 입력해 주세요.
                    </p>
                    <TextField
                      label="카드번호"
                      placeholder="숫자 15~16자리"
                      inputMode="numeric"
                      autoComplete="cc-number"
                      maxLength={16}
                      value={cardNumber}
                      onChange={(event) => {
                        setCardNumber(event.target.value.replace(/\D/g, ''))
                      }}
                    />
                    <div style={{ maxWidth: 160 }}>
                      <TextField
                        label="CVC"
                        placeholder="뒷면 3자리"
                        inputMode="numeric"
                        autoComplete="cc-csc"
                        maxLength={3}
                        value={cvc}
                        onChange={(event) => {
                          setCvc(event.target.value.replace(/\D/g, ''))
                        }}
                      />
                    </div>
                  </>
                )}
                <div style={{ display: 'flex', gap: 8 }}>
                  <Button
                    disabled={cancel.isPending || !cardReady}
                    onClick={() => {
                      cancel.mutate()
                    }}
                  >
                    {cancel.isPending ? '취소 처리 중…' : '취소 확정'}
                  </Button>
                  <Button variant="ghost" disabled={cancel.isPending} onClick={resetCancel}>
                    그대로 두기
                  </Button>
                </div>
              </div>
            ) : (
              <Button
                variant="ghost"
                onClick={() => {
                  setAskingCancel(true)
                }}
              >
                참여 취소하기
              </Button>
            )}
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
            <p className="small muted mt8" style={{ marginBottom: 0 }}>
              상품을 받지 못했거나 주문과 다른 물건이 왔다면{' '}
              <Link to={`/orders/${item.id}/dispute`}>이의를 제기</Link>하세요. 검토가 끝날 때까지
              자동 구매확정이 보류됩니다.
            </p>
          </>
        )}
      </div>
    </div>
  )
}

/**
 * 배송 레코드는 모집이 끝나야 생깁니다. 그 전에 DLV-05 를 부르면 404 이므로
 * 배송이 잡힌 뒤의 상태에서만 조회합니다.
 */
const DELIVERY_TRACKED_STATUS: readonly ParticipationStatus[] = [
  'FULFILLED',
  'SHIPPING',
  'DELIVERED',
  'CONFIRMED',
  'SETTLED',
  'RETURNING',
  'RETURNED',
]

function hasDelivery(status: ParticipationStatus): boolean {
  return DELIVERY_TRACKED_STATUS.includes(status)
}

/**
 * 배송 조회 (DLV-05).
 *
 * 목록 응답에는 배송완료 시각만 있어 택배사·송장번호는 이 응답에만 있습니다.
 * 접힌 건까지 매번 부르지 않도록 펼쳤을 때만 조회합니다.
 */
function DeliveryRow({ participationId, open }: { participationId: string; open: boolean }) {
  const delivery = useQuery({
    ...deliveryQueries.forParticipation(participationId),
    enabled: open,
  })

  if (!open) return null
  if (delivery.isPending) return <DataRow label="배송">불러오는 중…</DataRow>
  if (delivery.isError) return <DataRow label="배송">{toMessage(delivery.error)}</DataRow>

  const { status, carrierName, trackingNumber, shippedAt } = delivery.data

  return (
    <DataRow label="배송">
      {displayFor(DELIVERY_STATUS_DISPLAY, status, '배송 상태').label}
      {trackingNumber === null ? (
        <>
          <br />
          <span className="small muted">송장 등록 전입니다.</span>
        </>
      ) : (
        <>
          <br />
          <span className="small muted">
            {carrierName ?? '택배사 미상'} <span className="mono">{trackingNumber}</span>
          </span>
          {shippedAt !== null && (
            <>
              <br />
              <span className="small muted">{date(shippedAt)} 발송</span>
            </>
          )}
        </>
      )}
    </DataRow>
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
function EscrowRow({ participationId, open }: { participationId: string; open: boolean }) {
  const escrow = useQuery({ ...participationQueries.escrow(participationId), enabled: open })

  if (!open) return null
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

