/**
 * 플랫폼 정책값과 금액 계산.
 *
 * 기준 문서: `docs/공구한_API_명세서_v1.md` (ERD v2 우선).
 * 목업(mockup/assets/app.js)과 다른 점:
 *   - 플랫폼 수수료: 건당 정액 10,000원 → 분담금 합계의 5% 정률 (명세서 4장)
 *   - 배송비: 플랫폼 고정값 → 판매자가 공동구매별로 입력 (명세서 4장)
 *   - 도서산간 추가금: MVP 제외 (명세서 0장 / 19장)
 *
 * 서버가 최종 금액을 확정하므로 이 함수들은 화면 미리보기 용도입니다.
 * 실제 청구 금액은 항상 GRP-03 / PAY-01 응답을 신뢰하세요.
 */

/** 관리자 정책 API(ADM-03)로 덮어쓸 수 있는 기본값 */
export const POLICY_DEFAULTS = {
  /** 분담금 합계의 5%, 원 단위 버림 */
  platformFeeRate: 0.05,
  /** 카드·카카오페이 결제 슬롯 유효시간 */
  cardSlotTtlSeconds: 300,
  kakaopaySlotTtlSeconds: 300,
  /** 디지털 자산은 10분 */
  cryptoSlotTtlSeconds: 600,
  cryptoQuoteTtlSeconds: 600,
  cryptoBufferRate: 0.003,
  /** 배송완료 후 자동 구매확정 */
  autoConfirmDays: 7,
  /** 판매자 반품 승인 기한 (영업일) */
  returnApprovalBusinessDays: 3,
  shippingDeadlineBusinessDays: 3,
  /** 정산 가능 시점: 배송완료 + 7일 */
  settlementEligibleDays: 7,
  /** 목표 모집 인원 최소값 (명세서 4장) */
  minTargetCount: 2,
} as const

export type PolicyKey = keyof typeof POLICY_DEFAULTS

export interface GroupBuyTerms {
  /** 공동구매 1회분 상품 총액 */
  productPrice: number
  /** 목표 모집 인원 (2 이상) */
  targetCount: number
  /** 판매자가 입력한 1인 배송비 */
  shippingFee: number
}

export interface AmountBreakdown {
  /** 상품 분담금 = 상품 가격 ÷ 목표 인원, 원 단위 올림 */
  unitShareAmount: number
  shippingFee: number
  /** 1인 결제금액 = 분담금 + 배송비 */
  totalAmount: number
  /** 절상 차액 = 분담금 합계 − 상품 가격 (판매자 귀속) */
  roundingSurplus: number
  platformFeeRate: number
  /** 분담금 합계 × 수수료율, 원 단위 버림 */
  estimatedPlatformFee: number
  /** 분담금 합계 + 배송비 합계 − 수수료 */
  estimatedSettlementAmount: number
}

/** 상품 분담금. 명세서: 원 단위 올림하여 생성 시 확정하고 이후 재계산하지 않습니다. */
export function unitShareAmount({ productPrice, targetCount }: GroupBuyTerms): number {
  if (targetCount <= 0) return 0
  return Math.ceil(productPrice / targetCount)
}

/** 1인이 실제로 결제하는 금액 */
export function payableAmount(terms: GroupBuyTerms): number {
  return unitShareAmount(terms) + terms.shippingFee
}

/**
 * 공동구매 생성 화면(GRP-03)의 미리보기 계산.
 * `confirmedCount` 를 넘기면 그 인원만 구매확정한 경우의 정산액을 계산합니다.
 */
export function calculateAmounts(
  terms: GroupBuyTerms,
  options: { confirmedCount?: number; platformFeeRate?: number } = {},
): AmountBreakdown {
  const { targetCount, productPrice, shippingFee } = terms
  const platformFeeRate = options.platformFeeRate ?? POLICY_DEFAULTS.platformFeeRate
  const confirmedCount = options.confirmedCount ?? targetCount

  const share = unitShareAmount(terms)
  const shareTotal = share * confirmedCount
  const shippingTotal = shippingFee * confirmedCount

  // 수수료는 배송비를 제외한 분담금 합계에만 붙고, 원 단위로 버립니다.
  const platformFee = Math.floor(shareTotal * platformFeeRate)

  return {
    unitShareAmount: share,
    shippingFee,
    totalAmount: share + shippingFee,
    roundingSurplus: share * targetCount - productPrice,
    platformFeeRate,
    estimatedPlatformFee: platformFee,
    estimatedSettlementAmount: shareTotal + shippingTotal - platformFee,
  }
}

/** 정상가 대비 할인율 (카드·상세 화면 표시용) */
export function discountRate(listPrice: number, share: number): number {
  if (listPrice <= 0) return 0
  return Math.round((1 - share / listPrice) * 100)
}

/** 모집 진행률 0~100 */
export function progressRate(paidCount: number, targetCount: number): number {
  if (targetCount <= 0) return 0
  return Math.min(100, Math.round((paidCount / targetCount) * 100))
}
