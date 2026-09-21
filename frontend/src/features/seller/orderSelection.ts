export function sellerOrdersPath(groupBuyId: string): string {
  return `/seller/orders?groupBuyId=${encodeURIComponent(groupBuyId)}`
}

/** 명시한 잘못된 ID를 다른 주문으로 바꾸지 않는다. 권한은 서버가 검증한다. */
export function orderSelection(requested: string | null, firstId: string | null) {
  if (requested === null) return { id: firstId, invalid: false }
  return /^[1-9]\d*$/.test(requested)
    ? { id: requested, invalid: false }
    : { id: null, invalid: true }
}
