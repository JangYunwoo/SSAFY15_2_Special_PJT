import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { DELIVERY_STATUS_DISPLAY } from '@/entities/model'
import { deliveryApi } from '@/entities/delivery/api'
import { CARRIERS, type Delivery, type DeliveryBoard } from '@/entities/delivery/model'
import { deliveryQueries } from '@/entities/delivery/queries'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { date, groupBuyNo, maskName, percent, phone } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { FieldRow, SelectField, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

export default function SellerOrdersPage() {
  useDocumentTitle('주문 관리')

  const groupBuys = useQuery(groupBuyQueries.sellerList({ size: 100 }))
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const options = groupBuys.data?.items ?? []
  const activeId = selectedId ?? options[0]?.id ?? null

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">주문 관리</h2>
          <p className="sec-sub">공동구매별 참여자 주문과 배송 상태를 확인합니다.</p>
        </div>
      </div>

      <div className="field-row" style={{ maxWidth: 520, marginBottom: 22 }}>
        <select
          className="input"
          aria-label="공동구매 선택"
          value={activeId ?? ''}
          onChange={(event) => {
            setSelectedId(event.target.value)
          }}
        >
          {options.map((item) => (
            <option key={item.id} value={item.id}>
              {item.productName} · {groupBuyNo(item.id)}
            </option>
          ))}
        </select>
      </div>

      {activeId === null ? (
        <EmptyState>등록한 공동구매가 없습니다.</EmptyState>
      ) : (
        <DeliveryBoardView groupBuyId={activeId} />
      )}
    </section>
  )
}

function DeliveryBoardView({ groupBuyId }: { groupBuyId: string }) {
  const query = useQuery(deliveryQueries.board(groupBuyId))

  return (
    <QueryBoundary query={query}>
      {(board) => (
        <div className="split">
          <div>
            <Panel title="참여자 주문" description="송장을 등록하면 배송 상태가 바뀝니다">
              <DeliveryTable board={board} groupBuyId={groupBuyId} />
            </Panel>
          </div>

          <aside>
            <Panel title="배송 현황" description="송장 등록률">
              <div className="amount">{percent(board.invoiceRegisteredRate, 0)}</div>
              <DataRow label="전체 주문">{board.totalCount}건</DataRow>
              <DataRow label="송장 등록">{board.registeredCount}건</DataRow>
              <DataRow label="발송 대기">{board.totalCount - board.registeredCount}건</DataRow>
              <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
                모집 완료 상태에서만 송장을 등록할 수 있고, 같은 송장번호는 중복 등록되지 않습니다.
              </p>
            </Panel>
          </aside>
        </div>
      )}
    </QueryBoundary>
  )
}

function DeliveryTable({ board, groupBuyId }: { board: DeliveryBoard; groupBuyId: string }) {
  const [editing, setEditing] = useState<string | null>(null)
  const editingDelivery = board.items.find((item) => item.id === editing)

  if (board.items.length === 0) {
    return <EmptyState>결제 완료된 참여자가 없습니다.</EmptyState>
  }

  return (
    <>
      <table className="list fixed">
        <colgroup>
          <col style={{ width: '18%' }} />
          <col style={{ width: '30%' }} />
          <col style={{ width: '14%' }} />
          <col style={{ width: '24%' }} />
          <col style={{ width: '14%' }} />
        </colgroup>
        <thead>
          <tr>
            <th>수령인</th>
            <th>주소</th>
            <th>상태</th>
            <th>송장</th>
            <th className="num">관리</th>
          </tr>
        </thead>
        <tbody>
          {board.items.map((delivery) => {
            const display = DELIVERY_STATUS_DISPLAY[delivery.status]
            return (
              <tr key={delivery.id}>
                <td>
                  <b>{maskName(delivery.recipientName)}</b>
                  <br />
                  <span className="small muted">{phone(delivery.recipientPhone)}</span>
                </td>
                <td>
                  <span className="small">
                    ({delivery.zipcode}) {delivery.address1} {delivery.address2}
                  </span>
                </td>
                <td>
                  <StateChip tone={display.tone}>{display.label}</StateChip>
                </td>
                <td>
                  {delivery.trackingNumber === null ? (
                    <span className="small muted">미등록</span>
                  ) : (
                    <>
                      <span className="small">{delivery.carrierName}</span>
                      <br />
                      <span className="mono">{delivery.trackingNumber}</span>
                    </>
                  )}
                </td>
                <td className="num">
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setEditing((current) => (current === delivery.id ? null : delivery.id))
                    }}
                  >
                    {delivery.trackingNumber === null ? '송장 등록' : '수정'}
                  </Button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>

      {editingDelivery !== undefined && (
        <InvoiceForm
          delivery={editingDelivery}
          groupBuyId={groupBuyId}
          onDone={() => {
            setEditing(null)
          }}
        />
      )}
    </>
  )
}

function InvoiceForm({
  delivery,
  groupBuyId,
  onDone,
}: {
  delivery: Delivery
  groupBuyId: string
  onDone: () => void
}) {
  const queryClient = useQueryClient()

  const [carrierCode, setCarrierCode] = useState(delivery.carrierCode ?? '')
  const [trackingNumber, setTrackingNumber] = useState(delivery.trackingNumber ?? '')
  const [shippedAt, setShippedAt] = useState(
    delivery.shippedAt === null
      ? new Date().toISOString().slice(0, 10)
      : delivery.shippedAt.slice(0, 10),
  )

  const register = useMutation({
    mutationFn: () =>
      deliveryApi.registerInvoice(delivery.id, {
        carrierCode,
        trackingNumber,
        shippedAt: new Date(shippedAt).toISOString(),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.sellerGroupBuys.deliveries(groupBuyId),
      })
      onDone()
    },
  })

  return (
    <div className="box mt16">
      <div className="guide-head">
        {maskName(delivery.recipientName)} · 송장 등록
        <span className="small muted"> (발송일 {date(shippedAt)})</span>
      </div>

      <FieldRow>
        <SelectField
          label="택배사"
          required
          placeholder="선택"
          value={carrierCode}
          onChange={(event) => {
            setCarrierCode(event.target.value)
          }}
          options={CARRIERS.map((carrier) => ({ value: carrier.code, label: carrier.name }))}
        />
        <TextField
          label="송장번호"
          required
          inputMode="numeric"
          value={trackingNumber}
          onChange={(event) => {
            setTrackingNumber(event.target.value.replace(/\D/g, ''))
          }}
        />
        <TextField
          label="발송일"
          type="date"
          required
          value={shippedAt}
          onChange={(event) => {
            setShippedAt(event.target.value)
          }}
        />
      </FieldRow>

      {register.isError && (
        <p className="ai-hint fail" role="alert">
          {toMessage(register.error)}
        </p>
      )}

      <div className="order-actions">
        <Button variant="ghost" onClick={onDone}>
          취소
        </Button>
        <Button
          disabled={carrierCode === '' || trackingNumber === '' || register.isPending}
          onClick={() => {
            register.mutate()
          }}
        >
          {register.isPending ? '등록 중…' : '등록하기'}
        </Button>
      </div>
    </div>
  )
}
