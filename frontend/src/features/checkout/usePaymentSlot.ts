import { useEffect, useSyncExternalStore } from 'react'

import type { PaymentMethod } from '@/entities/model'
import { paymentApi } from '@/entities/payment/api'
import { newIdempotencyKey } from '@/shared/api/client'
import { useNow } from '@/shared/lib/hooks/useNow'

import { useCheckoutStore } from './checkoutStore'
import { isUsablePayment, PaymentSlotController, selectionKey } from './paymentSlotController'
import { releasePaymentSlot } from './releasePaymentSlot'

// SPA 재진입도 이전 화면의 해제를 기다린다. 전체 새로고침/탭 종료에서는
// 네트워크 완료를 보장할 수 없으므로 서버의 슬롯 TTL 방어가 여전히 필요하다.
const controller = new PaymentSlotController({
  prepare: paymentApi.prepare,
  abort: releasePaymentSlot,
  newKey: newIdempotencyKey,
  now: Date.now,
})

interface Args {
  deliveryRequest?: string | null
  groupBuyId: string
  addressId: string | null
  method: PaymentMethod | null
  cryptoAssetId?: string | null
}

export function usePaymentSlot({ groupBuyId, addressId, method, cryptoAssetId = null, deliveryRequest = null }: Args) {
  const snapshot = useSyncExternalStore(controller.subscribe, controller.getSnapshot)
  const now = useNow()
  const setPaymentId = useCheckoutStore((state) => state.setPaymentId)
  const selection = method !== null && addressId !== null &&
    (method !== 'CRYPTO' || (cryptoAssetId?.trim().length ?? 0) > 0)
    ? { groupBuyId, addressId, method, cryptoAssetId: method === 'CRYPTO' ? cryptoAssetId : null, deliveryRequest }
    : null

  useEffect(() => {
    controller.select(method !== null && addressId !== null &&
      (method !== 'CRYPTO' || (cryptoAssetId?.trim().length ?? 0) > 0)
      ? { groupBuyId, addressId, method, cryptoAssetId: method === 'CRYPTO' ? cryptoAssetId : null, deliveryRequest }
      : null)
    return () => { controller.select(null) }
  }, [groupBuyId, addressId, method, cryptoAssetId, deliveryRequest])

  const matches = selectionKey(selection) === selectionKey(snapshot.selection)
  const payment = matches && !snapshot.busy && isUsablePayment(snapshot.payment, selection, now)
    ? snapshot.payment : null

  useEffect(() => { setPaymentId(payment?.paymentId ?? null) }, [payment, setPaymentId])

  return {
    payment,
    isPreparing: snapshot.busy || (!matches && selection !== null),
    error: snapshot.error,
    retry: controller.retry,
    markSettled: controller.handoff,
    isExpired: matches && snapshot.payment !== null &&
      Date.parse(snapshot.payment.slotExpiresAt) <= now,
  }
}
