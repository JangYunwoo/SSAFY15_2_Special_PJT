import { useCallback, useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'

import type { PaymentMethod } from '@/entities/model'
import { paymentApi } from '@/entities/payment/api'
import type { Payment } from '@/entities/payment/model'
import { newIdempotencyKey } from '@/shared/api/client'

import { useCheckoutStore } from './checkoutStore'

interface Args {
  groupBuyId: string
  addressId: string | null
  method: PaymentMethod
  cryptoAssetId?: string | null
}

interface Slot {
  payment: Payment | null
  isPreparing: boolean
  error: unknown
  /**
   * 승인이 끝난 결제를 해제 대상에서 제외합니다.
   * 승인 성공 직후 호출하지 않으면 화면을 벗어날 때 PAY-03(abort)이 나가
   * 방금 확정한 결제를 건드리게 됩니다.
   */
  markSettled: () => void
}

/**
 * PAY-01 로 결제 슬롯을 선점합니다.
 *
 * 결제수단을 고른 시점에 슬롯을 잡아, 카드번호를 입력하는 동안에도 자리가
 * 유지되게 합니다. 수단을 바꾸면 슬롯 TTL(카드·카카오 5분 / 코인 10분)과
 * 결제 행 자체가 달라지므로, 이전 결제를 해제하고 새로 준비합니다.
 *
 * 준비 요청을 useQuery 로 다루는 이유:
 * (공동구매, 결제수단, 자산) 조합을 키로 잡으면 같은 조합에서 리렌더나
 * StrictMode 이중 실행이 일어나도 요청이 한 번만 나갑니다. 멱등키도 조합별로
 * 하나만 만들어 재사용하므로 서버가 슬롯을 두 개 잡지 않습니다.
 */
export function usePaymentSlot({
  groupBuyId,
  addressId,
  method,
  cryptoAssetId = null,
}: Args): Slot {
  const setPaymentId = useCheckoutStore((state) => state.setPaymentId)
  const hasRequiredAsset = method !== 'CRYPTO' || (cryptoAssetId?.trim().length ?? 0) > 0

  const idempotencyKeys = useRef(new Map<string, string>())

  const idempotencyKeyFor = (key: string): string => {
    const existing = idempotencyKeys.current.get(key)
    if (existing !== undefined) return existing
    const created = newIdempotencyKey()
    idempotencyKeys.current.set(key, created)
    return created
  }

  const query = useQuery({
    // 배송지까지 키에 넣습니다. 배송지가 바뀌면 결제 금액 검증 대상이 달라져
    // 같은 슬롯을 재사용할 수 없습니다.
    queryKey: ['payments', 'prepare', groupBuyId, method, cryptoAssetId, addressId],
    queryFn: () => {
      // enabled 로 막아두지만 타입을 좁히려면 여기서도 확인해야 합니다.
      if (addressId === null) throw new Error('배송지가 선택되지 않았습니다.')
      // enabled=false여도 수동 재조회는 queryFn을 실행할 수 있으므로 함께 방어한다.
      if (method === 'CRYPTO' && (cryptoAssetId?.trim().length ?? 0) === 0) {
        throw new Error('결제할 디지털 자산을 먼저 선택해 주세요.')
      }
      return paymentApi.prepare(
        groupBuyId,
        { method, addressId, cryptoAssetId },
        idempotencyKeyFor(`${groupBuyId}:${method}:${cryptoAssetId ?? '-'}:${addressId}`),
      )
    },
    enabled: addressId !== null && hasRequiredAsset,
    // 슬롯은 한 번 잡으면 다시 요청하지 않습니다. 만료는 타이머가 알려줍니다.
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    refetchOnMount: false,
    refetchOnReconnect: false,
  })

  const payment = query.data ?? null

  /** 이 훅이 잡아둔 결제. 조합이 바뀌거나 화면을 벗어날 때 해제합니다. */
  const heldPaymentId = useRef<string | null>(null)

  const markSettled = useCallback(() => {
    heldPaymentId.current = null
  }, [])

  useEffect(() => {
    if (payment === null) return

    heldPaymentId.current = payment.paymentId
    setPaymentId(payment.paymentId)

    return () => {
      const held = heldPaymentId.current
      if (held === null) return
      heldPaymentId.current = null
      // 결제수단을 바꾸거나 화면을 벗어나면 선점한 자리를 반납합니다.
      void paymentApi.abort(held).catch(() => undefined)
    }
  }, [payment, setPaymentId])

  return {
    payment,
    isPreparing: query.isFetching,
    error: query.error,
    markSettled,
  }
}
