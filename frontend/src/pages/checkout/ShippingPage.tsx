import { useEffect, useState } from 'react'

import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import { addressQueries } from '@/entities/address/queries'
import { DELIVERY_REQUESTS, type Address } from '@/entities/address/model'
import type { GroupBuyDetail } from '@/entities/groupBuy/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { useCheckoutStore } from '@/features/checkout/checkoutStore'
import { OrderSummary } from '@/features/checkout/OrderSummary'
import { groupBuyNo, phone, won } from '@/shared/lib/format'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { RadioGroup } from '@/shared/ui/RadioGroup'
import { SelectField } from '@/shared/ui/Field'
import { Steps } from '@/shared/ui/Steps'
import { QueryBoundary } from '@/shared/ui/states'

import { CHECKOUT_STEPS } from './steps'

export default function ShippingPage() {
  useDocumentTitle('배송지 선택')

  const { groupBuyId = '' } = useParams()
  const groupBuy = useQuery(groupBuyQueries.detail(groupBuyId))
  const addresses = useQuery(addressQueries.list())

  const startCheckout = useCheckoutStore((state) => state.startCheckout)
  useEffect(() => {
    startCheckout(groupBuyId)
  }, [groupBuyId, startCheckout])

  return (
    <main className="wrap">
      <Steps steps={CHECKOUT_STEPS} current={0} />

      <section className="section" style={{ paddingTop: 32 }}>
        <QueryBoundary query={groupBuy}>
          {(detail) => (
            <div className="split">
              <div>
                <OrderItem groupBuy={detail} />
                <QueryBoundary query={addresses}>
                  {(list) => <ShippingForm groupBuyId={groupBuyId} addresses={list} />}
                </QueryBoundary>
              </div>
              <aside className="sticky">
                <OrderSummary groupBuy={detail} />
              </aside>
            </div>
          )}
        </QueryBoundary>
      </section>
    </main>
  )
}

function OrderItem({ groupBuy }: { groupBuy: GroupBuyDetail }) {
  return (
    <div className="panel">
      <div className="order-item">
        <div className="thumb">
          {groupBuy.thumbnailUrl !== null && <img src={groupBuy.thumbnailUrl} alt="" />}
        </div>
        <div className="body">
          <div className="name">{groupBuy.productName}</div>
          <div className="small muted">
            공동구매 {groupBuyNo(groupBuy.id)} · {groupBuy.sellerName}
          </div>
          <div className="small mt8">
            1인 분담금 {won(groupBuy.unitShareAmount)} + 배송비 {won(groupBuy.shippingFee)}
          </div>
        </div>
      </div>
    </div>
  )
}

function ShippingForm({
  groupBuyId,
  addresses,
}: {
  groupBuyId: string
  addresses: readonly Address[]
}) {
  const navigate = useNavigate()
  const setShipping = useCheckoutStore((state) => state.setShipping)

  const defaultAddress = addresses.find((item) => item.isDefault) ?? addresses[0]
  const [addressId, setAddressId] = useState(defaultAddress?.id ?? '')
  const [deliveryRequest, setDeliveryRequest] = useState('')

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    if (!addressId) return
    setShipping({ addressId, deliveryRequest: deliveryRequest || null })
    void navigate(`/checkout/${groupBuyId}/payment`)
  }

  if (addresses.length === 0) {
    return (
      <div className="panel mt16">
        <h3>배송지 등록</h3>
        <p className="sub">등록된 배송지가 없습니다</p>
        <p className="para">
          공동구매 상품을 받을 주소가 필요합니다. 마이페이지에서 배송지를 먼저 등록해 주세요.
        </p>
      </div>
    )
  }

  return (
    <form className="panel mt16" onSubmit={submit}>
      <h3>배송지 선택</h3>
      <p className="sub">등록된 배송지 중에서 고릅니다</p>

      <RadioGroup
        legend="배송지"
        value={addressId}
        onChange={setAddressId}
        options={addresses.map((address) => ({
          value: address.id,
          label: (
            <span>
              {address.recipientName} · {phone(address.recipientPhone)}
              <span className="rowsub">
                ({address.zipcode}) {address.address1} {address.address2}
              </span>
            </span>
          ),
        }))}
      />

      <SelectField
        label="배송 요청사항"
        placeholder="배송 요청사항 선택"
        value={deliveryRequest}
        onChange={(event) => {
          setDeliveryRequest(event.target.value)
        }}
        options={DELIVERY_REQUESTS.map((request) => ({ value: request, label: request }))}
      />

      <Button type="submit" block className="mt24" disabled={!addressId}>
        결제수단 선택하기
      </Button>
    </form>
  )
}
