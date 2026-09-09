/**
 * 목 서버의 인메모리 저장소.
 * 새로고침하면 초기화됩니다. 백엔드가 붙으면 mocks 폴더 전체를 지웁니다.
 */
import type { Address } from '@/entities/address/model'
import type { GroupBuyDetail, GroupBuySummary } from '@/entities/groupBuy/model'
import type { ParticipationSummary } from '@/entities/participation/model'
import type { Notification } from '@/entities/notification/model'
import type { Payment } from '@/entities/payment/model'
import type { Product } from '@/entities/product/model'
import type { ReturnRequestSummary } from '@/entities/returnRequest/model'
import type { SettlementSummary } from '@/entities/settlement/model'

import { CATEGORY_SEEDS, GROUP_BUY_SEEDS, type GroupBuySeed } from './fixtures/groupBuys'

const HOUR_MS = 3_600_000
const DAY_MS = 86_400_000

const iso = (offsetMs: number): string => new Date(Date.now() + offsetMs).toISOString()

function toSummary(seed: GroupBuySeed): GroupBuySummary {
  return {
    id: seed.id,
    productId: seed.productId,
    productName: seed.productName,
    thumbnailUrl: seed.thumbnailUrl,
    sellerName: seed.sellerName,
    categoryId: seed.categoryId,
    categoryName: seed.categoryName,
    status: seed.status,
    listPrice: seed.listPrice,
    productPrice: seed.productPrice,
    unitShareAmount: seed.unitShareAmount,
    shippingFee: seed.shippingFee,
    targetCount: seed.targetCount,
    paidCount: seed.paidCount,
    recruitEndAt: iso(seed.hoursLeft * HOUR_MS),
    createdAt: new Date(seed.openedAt).toISOString(),
  }
}

function toDetail(seed: GroupBuySeed): GroupBuyDetail {
  return {
    ...toSummary(seed),
    description: seed.description,
    images: [],
    brand: null,
    modelName: null,
    color: null,
    roundingSurplus: seed.unitShareAmount * seed.targetCount - seed.productPrice,
    recruitStartAt: new Date(seed.openedAt).toISOString(),
    withdrawalRestrictionReason: null,
    sellerSuspended: false,
  }
}

const addresses: Address[] = [
  {
    id: '501',
    recipientName: '김민수',
    recipientPhone: '01012345678',
    zipcode: '34141',
    address1: '대전광역시 유성구 대학로 291',
    address2: '101동 1203호',
    isDefault: true,
    deliveryRequest: null,
  },
]

/** 거래관리 화면의 3건 (목업 orders.html) */
const participations: ParticipationSummary[] = [
  {
    id: '801',
    groupBuyId: '1024',
    productName: 'MacBook Air M4 16GB',
    sellerName: 'TECHSTORE',
    status: 'PAID',
    groupBuyStatus: 'RECRUITING',
    escrowStatus: 'HELD',
    method: 'CARD',
    shareAmount: 1_040_000,
    shippingFee: 4_000,
    totalAmount: 1_044_000,
    paidAt: iso(-11 * DAY_MS),
    targetCount: 10,
    paidCount: 7,
    recruitEndAt: iso(3.5 * DAY_MS),
    deliveredAt: null,
    autoConfirmAt: null,
    withdrawalDeadlineAt: null,
    confirmedAt: null,
    autoConfirmed: false,
  },
  {
    id: '802',
    groupBuyId: '1019',
    productName: '프리미엄 캡슐 커피 100입',
    sellerName: 'COFFEE HOUSE',
    status: 'DELIVERED',
    groupBuyStatus: 'FULFILLED',
    escrowStatus: 'HELD',
    method: 'KAKAOPAY',
    shareAmount: 32_500,
    shippingFee: 4_000,
    totalAmount: 36_500,
    paidAt: iso(-24 * DAY_MS),
    targetCount: 50,
    paidCount: 50,
    recruitEndAt: iso(-10 * DAY_MS),
    deliveredAt: iso(-3 * DAY_MS),
    autoConfirmAt: iso(4 * DAY_MS),
    withdrawalDeadlineAt: iso(4 * DAY_MS),
    confirmedAt: null,
    autoConfirmed: false,
  },
  {
    id: '803',
    groupBuyId: '0987',
    productName: '홈 트레이닝 밴드 세트',
    sellerName: 'FIT WORKS',
    status: 'SETTLED',
    groupBuyStatus: 'FULFILLED',
    escrowStatus: 'RELEASED',
    method: 'CARD',
    shareAmount: 24_900,
    shippingFee: 4_000,
    totalAmount: 28_900,
    paidAt: iso(-39 * DAY_MS),
    targetCount: 40,
    paidCount: 40,
    recruitEndAt: iso(-30 * DAY_MS),
    deliveredAt: iso(-25 * DAY_MS),
    autoConfirmAt: iso(-18 * DAY_MS),
    withdrawalDeadlineAt: iso(-18 * DAY_MS),
    confirmedAt: iso(-18 * DAY_MS),
    autoConfirmed: true,
  },
]

/** 판매자 상품 4건 (목업 seller-center/products.html) */
const products: Product[] = [
  {
    id: '301',
    name: 'MacBook Air M4 16GB',
    brand: 'Apple',
    modelName: 'MGN63KH/A',
    color: '미드나이트',
    categoryId: '11',
    categoryName: '전자제품',
    price: 10_400_000,
    description: '13인치 MacBook Air M4 · 16GB 통합 메모리 · 512GB SSD 모델입니다.',
    images: [],
    aiExtracted: true,
    withdrawalRestrictionReasonId: null,
    withdrawalRestrictionReason: null,
    restrictionReviewStatus: 'NONE',
    linkedGroupBuyId: '1024',
    linkedGroupBuyStatus: 'RECRUITING',
    createdAt: iso(-20 * DAY_MS),
  },
  {
    id: '308',
    name: '게이밍 마우스패드 XL',
    brand: 'KEY WORKS',
    modelName: 'KW-PAD-XL',
    color: '블랙',
    categoryId: '11',
    categoryName: '전자제품',
    price: 996_000,
    description: '900×400mm 대형 게이밍 마우스패드입니다.',
    images: [],
    aiExtracted: false,
    withdrawalRestrictionReasonId: null,
    withdrawalRestrictionReason: null,
    restrictionReviewStatus: 'NONE',
    linkedGroupBuyId: '1008',
    linkedGroupBuyStatus: 'FULFILLED',
    createdAt: iso(-40 * DAY_MS),
  },
  {
    id: '309',
    name: 'USB-C 충전기 65W',
    brand: 'TECHSTORE',
    modelName: 'TS-65W',
    color: '화이트',
    categoryId: '11',
    categoryName: '전자제품',
    price: 800_000,
    description: 'GaN 65W 3포트 충전기입니다.',
    images: [],
    aiExtracted: false,
    withdrawalRestrictionReasonId: null,
    withdrawalRestrictionReason: null,
    restrictionReviewStatus: 'NONE',
    linkedGroupBuyId: null,
    linkedGroupBuyStatus: null,
    createdAt: iso(-60 * DAY_MS),
  },
  {
    id: '307',
    name: '무접점 기계식 키보드',
    brand: 'KEY WORKS',
    modelName: 'KW-87',
    color: '화이트',
    categoryId: '11',
    categoryName: '전자제품',
    price: 2_975_000,
    description: '정전용량 무접점 방식 87키 텐키리스 키보드입니다.',
    images: [],
    aiExtracted: true,
    withdrawalRestrictionReasonId: null,
    withdrawalRestrictionReason: null,
    restrictionReviewStatus: 'NONE',
    linkedGroupBuyId: null,
    linkedGroupBuyStatus: null,
    createdAt: iso(-5 * DAY_MS),
  },
]

/** 판매자 반품 3건 (목업 seller-center/returns.html) */
const returns: ReturnRequestSummary[] = [
  {
    id: 'R-1008-12',
    participationId: '901',
    groupBuyId: '1008',
    productName: '게이밍 마우스패드 XL',
    buyerName: '이수연',
    reasonType: 'CHANGE_OF_MIND',
    detailReason: '생각한 크기보다 커서 반품합니다.',
    status: 'REQUESTED',
    refundAmount: 16_900,
    returnShippingFee: 8_000,
    shippingFeeBearer: 'BUYER',
    requestedAt: iso(-1 * DAY_MS),
    autoApproveAt: iso(2 * DAY_MS),
    postConfirm: false,
    adminReviewRequired: false,
  },
  {
    id: 'R-1008-07',
    participationId: '902',
    groupBuyId: '1008',
    productName: '게이밍 마우스패드 XL',
    buyerName: '박정훈',
    reasonType: 'DEFECTIVE',
    detailReason: '표면에 스크래치가 있습니다.',
    status: 'PICKED_UP',
    refundAmount: 28_900,
    returnShippingFee: 8_000,
    shippingFeeBearer: 'SELLER',
    requestedAt: iso(-5 * DAY_MS),
    autoApproveAt: null,
    postConfirm: false,
    adminReviewRequired: false,
  },
  {
    id: 'R-0952-04',
    participationId: '903',
    groupBuyId: '0952',
    productName: 'USB-C 충전기 65W',
    buyerName: '정하원',
    reasonType: 'CHANGE_OF_MIND',
    detailReason: '단순 변심',
    status: 'REFUNDED',
    refundAmount: 24_000,
    returnShippingFee: 8_000,
    shippingFeeBearer: 'BUYER',
    requestedAt: iso(-30 * DAY_MS),
    autoApproveAt: null,
    postConfirm: true,
    adminReviewRequired: true,
  },
]

/** 판매자 정산 3건 (목업 seller-center/settlement.html) */
const settlements: SettlementSummary[] = [
  {
    settlementId: '15001',
    groupBuyId: '1024',
    productName: 'MacBook Air M4 16GB',
    status: 'PENDING',
    targetCount: 10,
    paidCount: 7,
    confirmedCount: 0,
    netAmount: 9_918_000,
    settlementEligibleAt: null,
    paidOutAt: null,
  },
  {
    settlementId: '15002',
    groupBuyId: '1008',
    productName: '게이밍 마우스패드 XL',
    status: 'PARTIAL',
    targetCount: 40,
    paidCount: 40,
    confirmedCount: 38,
    netAmount: 1_051_890,
    settlementEligibleAt: iso(2 * DAY_MS),
    paidOutAt: null,
  },
  {
    settlementId: '15003',
    groupBuyId: '0952',
    productName: 'USB-C 충전기 65W',
    status: 'COMPLETED',
    targetCount: 25,
    paidCount: 25,
    confirmedCount: 25,
    netAmount: 860_000,
    settlementEligibleAt: iso(-36 * DAY_MS),
    paidOutAt: iso(-33 * DAY_MS),
  },
]

/** 알림 5건 (명세서 14장 · 배송 알림은 제공하지 않습니다) */
const notifications: Notification[] = [
  {
    id: '9001',
    type: 'GROUP_BUY_FULFILLED',
    title: '모집이 완료되었습니다',
    body: 'MacBook Air M4 16GB 공동구매가 목표 인원을 채워 결제가 확정되었습니다.',
    linkUrl: '/orders',
    readAt: null,
    createdAt: iso(-2 * 3_600_000),
  },
  {
    id: '9002',
    type: 'RETURN_STATUS_CHANGED',
    title: '반품이 승인되었습니다',
    body: '무선 노이즈캔슬링 헤드폰 반품 요청이 승인되어 회수가 진행됩니다.',
    linkUrl: '/orders',
    readAt: null,
    createdAt: iso(-8 * 3_600_000),
  },
  {
    id: '9003',
    type: 'REFUND_COMPLETED',
    title: '환불이 완료되었습니다',
    body: '원목 체스 세트 공동구매 모집 실패로 32,000원이 환불되었습니다.',
    linkUrl: '/orders',
    readAt: null,
    createdAt: iso(-1 * DAY_MS),
  },
  {
    id: '9004',
    type: 'SETTLEMENT_COMPLETED',
    title: '정산이 지급되었습니다',
    body: 'USB-C 충전기 65W 공동구매 정산금 760,000원이 지급되었습니다.',
    linkUrl: '/seller/settlements',
    readAt: iso(-2 * DAY_MS),
    createdAt: iso(-3 * DAY_MS),
  },
  {
    id: '9005',
    type: 'GROUP_BUY_FAILED',
    title: '모집이 종료되었습니다',
    body: '캠핑용 접이식 테이블 공동구매가 목표 인원을 채우지 못해 종료되었습니다.',
    linkUrl: null,
    readAt: iso(-4 * DAY_MS),
    createdAt: iso(-5 * DAY_MS),
  },
]

export const db = {
  categories: CATEGORY_SEEDS,
  groupBuys: GROUP_BUY_SEEDS.map(toSummary),
  groupBuyDetails: new Map(GROUP_BUY_SEEDS.map((seed) => [seed.id, toDetail(seed)])),
  addresses,
  participations,
  products,
  returns,
  settlements,
  notifications,
  /** 결제 준비로 생성된 PROCESSING 결제들 */
  payments: new Map<string, Payment>(),
  nextId: 9000,
}
