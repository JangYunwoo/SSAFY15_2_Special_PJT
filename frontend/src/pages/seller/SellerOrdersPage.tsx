import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { DELIVERY_STATUS, DELIVERY_STATUS_DISPLAY, type DeliveryStatus } from '@/entities/model'
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
        /* 공동구매를 바꾸면 선택·편집 상태를 들고 가지 않도록 다시 마운트합니다. */
        <DeliveryBoardView key={activeId} groupBuyId={activeId} />
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
            <Panel title="참여자 주문" description="송장을 등록하고 배송 상태를 넘깁니다">
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

            <Panel className="mt16" title="배송 상태" description="상태를 넘기면 생기는 일">
              <DataRow label="발송대기">송장 등록 전</DataRow>
              <DataRow label="배송중">송장을 등록하고 발송한 상태</DataRow>
              <DataRow label="배송완료">구매확정 대기 시작</DataRow>
              <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
                자동 구매확정 기한과 청약철회 기한은 배송완료 시각부터 계산됩니다. 배송완료로 넘겨야
                구매자가 구매확정·반품을 진행할 수 있으니 실제 수령을 확인한 뒤 처리하세요.
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
  const [selected, setSelected] = useState<readonly string[]>([])

  const editingDelivery = board.items.find((item) => item.id === editing)

  /* 목록이 갱신되며 사라진 건은 선택에서 빼고 셉니다. */
  const selectedIds = selected.filter((id) => board.items.some((item) => item.id === id))
  const allSelected = board.items.length > 0 && selectedIds.length === board.items.length

  if (board.items.length === 0) {
    return <EmptyState>결제 완료된 참여자가 없습니다.</EmptyState>
  }

  return (
    <>
      <BulkStatusBar
        groupBuyId={groupBuyId}
        items={board.items}
        selectedIds={selectedIds}
        onDone={() => {
          setSelected([])
        }}
      />

      <table className="list fixed">
        <colgroup>
          <col style={{ width: '5%' }} />
          <col style={{ width: '16%' }} />
          <col style={{ width: '25%' }} />
          <col style={{ width: '12%' }} />
          <col style={{ width: '20%' }} />
          <col style={{ width: '22%' }} />
        </colgroup>
        <thead>
          <tr>
            <th>
              <input
                type="checkbox"
                aria-label="전체 선택"
                checked={allSelected}
                onChange={(event) => {
                  setSelected(event.target.checked ? board.items.map((item) => item.id) : [])
                }}
              />
            </th>
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
                  <input
                    type="checkbox"
                    aria-label={`${maskName(delivery.recipientName)} 선택`}
                    checked={selectedIds.includes(delivery.id)}
                    onChange={() => {
                      setSelected((current) =>
                        current.includes(delivery.id)
                          ? current.filter((id) => id !== delivery.id)
                          : [...current, delivery.id],
                      )
                    }}
                  />
                </td>
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
                  <RowStatusSelect delivery={delivery} groupBuyId={groupBuyId} />
                  <Button
                    variant="ghost"
                    className="mt8"
                    onClick={() => {
                      setEditing((current) => (current === delivery.id ? null : delivery.id))
                    }}
                  >
                    {delivery.trackingNumber === null ? '송장 등록' : '송장 수정'}
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

/** select 의 value 는 string 이라, 보내기 전에 정의된 상태값인지 확인합니다. */
function toDeliveryStatus(value: string): DeliveryStatus | null {
  return DELIVERY_STATUS.find((status) => status === value) ?? null
}

/**
 * DLV-03 참여자 한 건의 배송 상태 변경.
 *
 * 어떤 값으로 넘어갈 수 있는지는 서버가 판단합니다(송장 없이 배송중 등).
 * 화면에서 미리 막지 않고 거부 사유를 그대로 보여줍니다.
 */
function RowStatusSelect({ delivery, groupBuyId }: { delivery: Delivery; groupBuyId: string }) {
  const queryClient = useQueryClient()

  const update = useMutation({
    mutationFn: (status: DeliveryStatus) => deliveryApi.updateStatus(delivery.id, status),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.sellerGroupBuys.deliveries(groupBuyId),
      })
    },
  })

  return (
    <>
      <select
        className="input"
        aria-label={`${maskName(delivery.recipientName)} 배송 상태 변경`}
        value={delivery.status}
        disabled={update.isPending}
        onChange={(event) => {
          const next = toDeliveryStatus(event.target.value)
          if (next !== null && next !== delivery.status) update.mutate(next)
        }}
      >
        {DELIVERY_STATUS.map((status) => (
          <option key={status} value={status}>
            {DELIVERY_STATUS_DISPLAY[status].label}
          </option>
        ))}
      </select>
      {update.isError && (
        <p className="small ai-hint fail mt8" role="alert" style={{ marginBottom: 0 }}>
          {toMessage(update.error)}
        </p>
      )}
    </>
  )
}

/**
 * DLV-04 선택한 참여자의 배송 상태 일괄 변경.
 *
 * 참여자가 많은 공동구매에서 한 건씩 넘기면 빠뜨리기 쉬워, 발송·수령 확인은
 * 묶어서 처리할 수 있게 둡니다.
 */
function BulkStatusBar({
  groupBuyId,
  items,
  selectedIds,
  onDone,
}: {
  groupBuyId: string
  items: readonly Delivery[]
  selectedIds: readonly string[]
  onDone: () => void
}) {
  const queryClient = useQueryClient()

  const bulk = useMutation({
    mutationFn: (status: DeliveryStatus) =>
      deliveryApi.updateStatusBulk(groupBuyId, status, [...selectedIds]),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.sellerGroupBuys.deliveries(groupBuyId),
      })
      onDone()
    },
  })

  const withoutInvoice = items.filter(
    (item) => selectedIds.includes(item.id) && item.trackingNumber === null,
  ).length
  const none = selectedIds.length === 0

  return (
    <div className="box" style={{ marginBottom: 16 }}>
      <div className="order-actions" style={{ marginTop: 0, alignItems: 'center' }}>
        <span className="small" style={{ flex: 1 }}>
          {none ? '상태를 바꿀 주문을 선택하세요.' : `${String(selectedIds.length)}건 선택`}
        </span>
        <Button
          variant="ghost"
          disabled={none || bulk.isPending}
          onClick={() => {
            bulk.mutate('SHIPPING')
          }}
        >
          배송중으로
        </Button>
        <Button
          disabled={none || bulk.isPending}
          onClick={() => {
            bulk.mutate('DELIVERED')
          }}
        >
          배송완료로
        </Button>
      </div>

      {withoutInvoice > 0 && (
        <p className="small muted mt8" style={{ marginBottom: 0 }}>
          선택한 {withoutInvoice}건은 송장이 없습니다. 송장을 먼저 등록해야 배송중으로 넘어갑니다.
        </p>
      )}

      {bulk.isError && (
        <p className="small ai-hint fail mt8" role="alert" style={{ marginBottom: 0 }}>
          {toMessage(bulk.error)}
        </p>
      )}
    </div>
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
