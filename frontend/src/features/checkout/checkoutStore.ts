import { create } from 'zustand'

interface CheckoutState {
  /** 어느 공동구매의 결제인지. 다른 건으로 넘어가면 초기화합니다. */
  groupBuyId: string | null
  addressId: string | null
  deliveryRequest: string | null
  /** PAY-01 로 만들어진 PROCESSING 결제 (= 선점된 슬롯) */
  paymentId: string | null

  startCheckout: (groupBuyId: string) => void
  setShipping: (input: { addressId: string; deliveryRequest: string | null }) => void
  setPaymentId: (paymentId: string | null) => void
  reset: () => void
}

const EMPTY = {
  groupBuyId: null,
  addressId: null,
  deliveryRequest: null,
  paymentId: null,
} as const

/**
 * 결제 단계 사이에서만 필요한 값들.
 *
 * URL 쿼리로 넘기면 배송지 ID가 주소창에 남고, 새로고침 시 슬롯을 재사용해야 할지
 * 판단하기 어려워집니다. persist 를 걸지 않았으므로 탭을 닫으면 사라지고,
 * 그때는 PAY-02 로 서버의 결제 상태를 다시 확인하면 됩니다.
 */
export const useCheckoutStore = create<CheckoutState>()((set, get) => ({
  ...EMPTY,

  startCheckout: (groupBuyId) => {
    // 다른 공동구매로 진입하면 이전 단계 값이 섞이지 않게 비웁니다.
    if (get().groupBuyId !== groupBuyId) set({ ...EMPTY, groupBuyId })
  },

  setShipping: ({ addressId, deliveryRequest }) => {
    set({ addressId, deliveryRequest })
  },

  setPaymentId: (paymentId) => {
    set({ paymentId })
  },

  reset: () => {
    set({ ...EMPTY })
  },
}))
