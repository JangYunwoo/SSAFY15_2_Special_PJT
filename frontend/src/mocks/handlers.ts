/**
 * MSW 핸들러. 명세서의 응답 봉투(success/data/meta/traceId)를 그대로 흉내냅니다.
 * 백엔드가 붙으면 .env 의 VITE_API_MOCKING=false 로 끄고, 이 폴더를 지우면 됩니다.
 */
import { delay, http, HttpResponse } from 'msw'

import type { AuthTokens } from '@/entities/auth/model'
import type { AmountPreview, GroupBuySummary } from '@/entities/groupBuy/model'
import type {
  CryptoAsset,
  CryptoQuote,
  KakaoPayReady,
  Payment,
  PreparePaymentRequest,
} from '@/entities/payment/model'
import type { AiExtraction } from '@/entities/product/model'
import type { ReturnPreview, ReturnPreviewRequest } from '@/entities/returnRequest/model'
import type { SettlementDetail } from '@/entities/settlement/model'
import { isSellerFault } from '@/entities/model'
import type { PageMeta } from '@/shared/api/types'
import { calculateAmounts, POLICY_DEFAULTS } from '@/shared/config/policy'

import { db } from './db'

const BASE = '/api/v1'

let traceSeq = 0
const traceId = (): string => `01JMOCK${String(++traceSeq).padStart(6, '0')}`

const ok = (data: unknown, meta: unknown = null) =>
  HttpResponse.json({ success: true, data, meta, traceId: traceId() })

const fail = (status: number, code: string, message: string, fieldErrors: never[] = []) =>
  HttpResponse.json(
    { success: false, error: { code, message, fieldErrors }, traceId: traceId() },
    { status },
  )

/** 목록 응답을 페이지로 잘라 meta 를 붙입니다. */
function paged(items: readonly unknown[], url: URL) {
  const page = Number(url.searchParams.get('page') ?? '0')
  const size = Math.min(Number(url.searchParams.get('size') ?? '20'), 100)
  const meta: PageMeta = {
    page,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  }
  return ok(items.slice(page * size, page * size + size), meta)
}

/**
 * 등록·수정 multipart 요청에서 product 파트(JSON)를 읽습니다.
 * 목에는 파일 저장소가 없으므로 images 파트는 개수만 세어 자리만 만듭니다.
 */
async function readUpsert(request: Request): Promise<Record<string, unknown>> {
  const form = await request.formData()
  const part = form.get('product')
  const json = typeof part === 'string' ? part : await (part?.text() ?? Promise.resolve('{}'))
  const files = form.getAll('images')
  const parsed = JSON.parse(json) as Record<string, unknown>
  return files.length === 0
    ? parsed
    : { ...parsed, images: files.map((_, index) => `mock://image-${String(index + 1)}`) }
}
const iso = (offsetMs: number): string => new Date(Date.now() + offsetMs).toISOString()

/* ---------- 목록 정렬 (목업 SORTERS 와 같은 규칙) ---------- */

const SORTERS: Record<string, (a: GroupBuySummary, b: GroupBuySummary) => number> = {
  popular: (a, b) => b.paidCount / b.targetCount - a.paidCount / a.targetCount,
  deadline: (a, b) => Date.parse(a.recruitEndAt) - Date.parse(b.recruitEndAt),
  newest: (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt),
  oldest: (a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt),
  priceAsc: (a, b) => a.unitShareAmount - b.unitShareAmount,
  priceDesc: (a, b) => b.unitShareAmount - a.unitShareAmount,
}

/**
 * 계정은 하나뿐입니다. 판매자 전환(SEL-05) 전에는 isSeller=false 이고,
 * 목에서는 전환 API 를 아직 흉내내지 않으므로 화면이 세션 플래그만 세웁니다.
 */
const tokens = (): AuthTokens => ({
  accessToken: 'mock.user.access',
  refreshToken: 'mock.user.refresh',
  tokenType: 'Bearer',
  expiresIn: 1800,
  actor: { id: '101', role: 'USER', isSeller: false, name: '공구왕' },
})

export const handlers = [
  /* ---------- 인증 ---------- */
  http.post(`${BASE}/auth/signup`, async ({ request }) => {
    await delay(300)
    const body = (await request.json()) as { email: string; nickname: string }
    // AUTH-01 은 계정만 만들고 토큰은 주지 않습니다 (백엔드 SignupResponse).
    return HttpResponse.json(
      {
        success: true,
        data: { id: '101', email: body.email, nickname: body.nickname },
        meta: null,
        traceId: traceId(),
      },
      { status: 201 },
    )
  }),
  http.post(`${BASE}/auth/login`, async () => {
    await delay(200)
    return ok(tokens())
  }),
  http.post(`${BASE}/auth/logout`, () => ok(null)),

  http.get(`${BASE}/users/me`, () => ok(db.me)),
  http.patch(`${BASE}/users/me`, async ({ request }) => {
    await delay(250)
    const body = (await request.json()) as Partial<typeof db.me>
    // 이메일과 가입일은 수정 대상이 아닙니다 (USR-02).
    db.me = {
      ...db.me,
      nickname: body.nickname ?? db.me.nickname,
      phone: body.phone ?? db.me.phone,
    }
    return ok(db.me)
  }),
  http.get(`${BASE}/sellers/me`, () =>
    ok({
      id: '201',
      email: 'seller@example.com',
      shopName: 'TECHSTORE',
      phone: '01098765432',
      settlementAccount: {
        bankCode: '004',
        accountNumber: '000-0000-0000',
        holderName: 'TECHSTORE',
      },
      returnAddress: {
        recipientName: 'TECHSTORE 물류',
        recipientPhone: '01098765432',
        zipcode: '06236',
        address1: '서울특별시 강남구 테헤란로 152',
        address2: '3층',
      },
      penaltyCount: 0,
      suspended: false,
      suspendedUntil: null,
    }),
  ),

  /* ---------- 배송지 ---------- */
  http.get(`${BASE}/users/me/addresses`, () => ok(db.addresses)),
  http.post(`${BASE}/users/me/addresses`, async ({ request }) => {
    await delay(200)
    const body = (await request.json()) as Omit<(typeof db.addresses)[number], 'id'>
    // 첫 배송지는 자동으로 기본이 되고, 기본으로 지정하면 나머지는 해제됩니다.
    const isDefault = db.addresses.length === 0 || body.isDefault
    if (isDefault) for (const item of db.addresses) item.isDefault = false
    const created = { ...body, isDefault, id: String(++db.nextId) }
    db.addresses.push(created)
    return ok(created)
  }),
  http.patch(`${BASE}/users/me/addresses/:addressId`, async ({ params, request }) => {
    await delay(200)
    const index = db.addresses.findIndex((item) => item.id === String(params.addressId))
    const current = db.addresses[index]
    if (current === undefined) return fail(404, 'RESOURCE_NOT_FOUND', '배송지를 찾을 수 없습니다.')
    const body = (await request.json()) as Partial<(typeof db.addresses)[number]>
    if (body.isDefault === true) for (const item of db.addresses) item.isDefault = false
    const updated = { ...current, ...body }
    db.addresses[index] = updated
    return ok(updated)
  }),
  http.delete(`${BASE}/users/me/addresses/:addressId`, async ({ params }) => {
    await delay(200)
    const index = db.addresses.findIndex((item) => item.id === String(params.addressId))
    const current = db.addresses[index]
    if (current === undefined) return fail(404, 'RESOURCE_NOT_FOUND', '배송지를 찾을 수 없습니다.')
    db.addresses.splice(index, 1)
    // 기본 배송지를 지웠으면 남은 첫 배송지를 기본으로 올립니다.
    const first = db.addresses[0]
    if (current.isDefault && first !== undefined) first.isDefault = true
    return ok(null)
  }),

  /* ---------- 카테고리 ---------- */
  http.get(`${BASE}/categories`, () => ok(db.categories)),

  /* ---------- 공동구매 (공개) ---------- */
  http.get(`${BASE}/group-buys`, async ({ request }) => {
    await delay(150)
    const url = new URL(request.url)
    const keyword = (url.searchParams.get('keyword') ?? '').trim().toLowerCase()
    const categoryId = url.searchParams.get('categoryId')
    const sort = url.searchParams.get('sort')

    let items = db.groupBuys.filter((item) => {
      if (categoryId && item.categoryId !== categoryId) return false
      if (!keyword) return true
      const haystack = `${item.productName} ${item.sellerName} ${item.categoryName}`.toLowerCase()
      return haystack.includes(keyword)
    })

    const sorter = sort === null ? undefined : SORTERS[sort]
    if (sorter) items = [...items].sort(sorter)

    return paged(items, url)
  }),

  http.get(`${BASE}/group-buys/:groupBuyId`, async ({ params }) => {
    await delay(150)
    const detail = db.groupBuyDetails.get(String(params.groupBuyId))
    if (!detail) return fail(404, 'RESOURCE_NOT_FOUND', '공동구매를 찾을 수 없습니다.')
    return ok(detail)
  }),

  /* ---------- 결제 ---------- */
  http.post(`${BASE}/group-buys/:groupBuyId/payments/prepare`, async ({ params, request }) => {
    await delay(400)
    const groupBuyId = String(params.groupBuyId)
    const detail = db.groupBuyDetails.get(groupBuyId)
    if (!detail) return fail(404, 'RESOURCE_NOT_FOUND', '공동구매를 찾을 수 없습니다.')
    if (detail.status !== 'RECRUITING') {
      return fail(409, 'GROUP_BUY_NOT_RECRUITING', '모집이 종료된 공동구매입니다.')
    }
    if (detail.paidCount >= detail.targetCount) {
      return fail(409, 'GROUP_BUY_FULL', '목표 인원이 모두 채워졌습니다.')
    }

    const body = (await request.json()) as PreparePaymentRequest
    const ttlSeconds =
      body.method === 'CRYPTO'
        ? POLICY_DEFAULTS.cryptoSlotTtlSeconds
        : POLICY_DEFAULTS.cardSlotTtlSeconds

    const payment: Payment = {
      paymentId: String(++db.nextId),
      groupBuyId,
      status: 'PROCESSING',
      method: body.method,
      amount: {
        shareAmount: detail.unitShareAmount,
        shippingFee: detail.shippingFee,
        totalAmount: detail.unitShareAmount + detail.shippingFee,
        currency: 'KRW',
      },
      slotExpiresAt: iso(ttlSeconds * 1000),
      remainingSeconds: ttlSeconds,
      participationId: null,
    }
    db.payments.set(payment.paymentId, payment)
    return ok(payment)
  }),

  http.get(`${BASE}/payments/:paymentId`, ({ params }) => {
    const payment = db.payments.get(String(params.paymentId))
    if (!payment) return fail(404, 'RESOURCE_NOT_FOUND', '결제 정보를 찾을 수 없습니다.')
    return ok({
      ...payment,
      remainingSeconds: Math.max(
        0,
        Math.ceil((Date.parse(payment.slotExpiresAt) - Date.now()) / 1000),
      ),
    })
  }),

  http.post(`${BASE}/payments/:paymentId/abort`, ({ params }) => {
    const payment = db.payments.get(String(params.paymentId))
    if (payment) payment.status = 'EXPIRED'
    return ok(null)
  }),

  http.post(`${BASE}/payments/:paymentId/card/approve`, async ({ params }) => {
    await delay(700)
    const payment = db.payments.get(String(params.paymentId))
    if (!payment) return fail(404, 'RESOURCE_NOT_FOUND', '결제 정보를 찾을 수 없습니다.')
    if (payment.status !== 'PROCESSING') {
      return fail(409, 'PAYMENT_ALREADY_PROCESSED', '이미 처리된 결제입니다.')
    }
    if (Date.parse(payment.slotExpiresAt) < Date.now()) {
      payment.status = 'EXPIRED'
      return fail(409, 'PAYMENT_SLOT_EXPIRED', '결제 가능 시간이 만료되었습니다.')
    }
    payment.status = 'PAID'
    payment.participationId = String(++db.nextId)
    return ok(payment)
  }),

  http.post(`${BASE}/payments/:paymentId/kakaopay/ready`, async ({ params }) => {
    await delay(300)
    const paymentId = String(params.paymentId)
    const ready: KakaoPayReady = {
      paymentId,
      tid: `T${paymentId}MOCK`,
      // 실제 리다이렉트 대신 완료 화면으로 바로 보냅니다.
      nextRedirectPcUrl: `/checkout/complete/${paymentId}`,
      expiresAt: iso(POLICY_DEFAULTS.kakaopaySlotTtlSeconds * 1000),
    }
    return ok(ready)
  }),

  http.post(`${BASE}/payments/kakaopay/approve`, async ({ request }) => {
    await delay(500)
    const body = (await request.json()) as { paymentId: string }
    const payment = db.payments.get(body.paymentId)
    if (!payment) return fail(404, 'RESOURCE_NOT_FOUND', '결제 정보를 찾을 수 없습니다.')
    payment.status = 'PAID'
    payment.participationId = String(++db.nextId)
    return ok(payment)
  }),

  http.get(`${BASE}/crypto/assets`, () => {
    const assets: CryptoAsset[] = [
      { id: '1', symbol: 'ETH', name: '이더리움', chainId: 80002, requiredConfirmations: 12 },
      { id: '2', symbol: 'USDT', name: '테더', chainId: 80002, requiredConfirmations: 3 },
      { id: '3', symbol: 'BTC', name: '비트코인', chainId: 0, requiredConfirmations: 3 },
      { id: '4', symbol: 'XRP', name: '리플', chainId: 0, requiredConfirmations: 4 },
    ]
    return ok(assets)
  }),

  http.post(`${BASE}/payments/:paymentId/crypto/quote`, async ({ params, request }) => {
    await delay(300)
    const payment = db.payments.get(String(params.paymentId))
    if (!payment) return fail(404, 'RESOURCE_NOT_FOUND', '결제 정보를 찾을 수 없습니다.')

    const { cryptoAssetId } = (await request.json()) as { cryptoAssetId: string }
    const rates: Record<string, { symbol: string; rate: number; confirmations: number }> = {
      '1': { symbol: 'ETH', rate: 4_820_000, confirmations: 12 },
      '2': { symbol: 'USDT', rate: 1_380.12, confirmations: 3 },
      '3': { symbol: 'BTC', rate: 132_400_000, confirmations: 3 },
      '4': { symbol: 'XRP', rate: 3_120, confirmations: 4 },
    }
    const asset = rates[cryptoAssetId] ?? rates['2']
    if (!asset) return fail(404, 'RESOURCE_NOT_FOUND', '지원하지 않는 자산입니다.')

    const buffer = POLICY_DEFAULTS.cryptoBufferRate
    const applied = asset.rate * (1 + buffer)
    const quote: CryptoQuote = {
      quoteId: String(++db.nextId),
      asset: asset.symbol,
      chainId: 80002,
      krwAmount: payment.amount.totalAmount,
      marketRate: asset.rate.toFixed(8),
      bufferRate: buffer.toFixed(6),
      appliedRate: applied.toFixed(8),
      assetAmount: (payment.amount.totalAmount / applied).toFixed(18),
      depositAddress: '0x71A0d9E2C4F3b8A1d5E6F7a8B9c0D1e2F3a4B5c6',
      requiredConfirmations: asset.confirmations,
      expiresAt: iso(POLICY_DEFAULTS.cryptoQuoteTtlSeconds * 1000),
    }
    return ok(quote)
  }),

  /* ---------- 참여 · 구매확정 ---------- */
  http.get(`${BASE}/users/me/participations`, async ({ request }) => {
    await delay(150)
    return paged(db.participations, new URL(request.url))
  }),

  http.post(`${BASE}/users/me/participations/:id/cancel`, async ({ params }) => {
    await delay(400)
    const target = db.participations.find((item) => item.id === String(params.id))
    if (!target) return fail(404, 'RESOURCE_NOT_FOUND', '참여 정보를 찾을 수 없습니다.')
    if (target.groupBuyStatus !== 'RECRUITING') {
      return fail(409, 'INVALID_STATE_TRANSITION', '모집이 종료되어 취소할 수 없습니다.')
    }
    target.status = 'REFUNDING'
    return ok(null)
  }),

  http.post(`${BASE}/users/me/participations/:id/confirm`, async ({ params }) => {
    await delay(500)
    const target = db.participations.find((item) => item.id === String(params.id))
    if (!target) return fail(404, 'RESOURCE_NOT_FOUND', '참여 정보를 찾을 수 없습니다.')
    if (target.status !== 'DELIVERED') {
      return fail(409, 'INVALID_STATE_TRANSITION', '배송완료 상태에서만 확정할 수 있습니다.')
    }
    target.status = 'CONFIRMED'
    target.confirmedAt = new Date().toISOString()
    target.escrowStatus = 'RELEASED'
    return ok(target)
  }),

  /* ---------- 반품 ---------- */
  http.post(`${BASE}/users/me/participations/:id/returns/preview`, async ({ params, request }) => {
    await delay(250)
    const target = db.participations.find((item) => item.id === String(params.id))
    if (!target) return fail(404, 'RESOURCE_NOT_FOUND', '참여 정보를 찾을 수 없습니다.')

    const { reasonType } = (await request.json()) as ReturnPreviewRequest
    const sellerFault = isSellerFault(reasonType)

    // 단순변심: 최초 배송비 미환불 + 반품비는 배송비의 2배(구매자 부담)
    const refundableOriginalShippingFee = sellerFault ? target.shippingFee : 0
    const returnShippingFee = target.shippingFee * 2
    const refundAmount = sellerFault
      ? target.shareAmount + target.shippingFee
      : target.shareAmount - returnShippingFee

    const preview: ReturnPreview = {
      eligible: target.withdrawalDeadlineAt !== null,
      ineligibleReason:
        target.withdrawalDeadlineAt === null ? '배송이 완료된 뒤에 신청할 수 있습니다.' : null,
      applicationDeadline: target.withdrawalDeadlineAt ?? iso(0),
      paymentAmount: target.totalAmount,
      refundableShareAmount: target.shareAmount,
      refundableOriginalShippingFee,
      returnShippingFee,
      refundAmount,
      shippingFeeBearer: sellerFault ? 'SELLER' : 'BUYER',
    }
    return ok(preview)
  }),

  http.post(`${BASE}/users/me/participations/:id/returns`, async ({ params }) => {
    await delay(500)
    const target = db.participations.find((item) => item.id === String(params.id))
    if (!target) return fail(404, 'RESOURCE_NOT_FOUND', '참여 정보를 찾을 수 없습니다.')
    target.status = 'RETURNING'
    return ok({ id: `R-${target.groupBuyId}-01` })
  }),

  http.get(`${BASE}/sellers/returns`, async ({ request }) => {
    await delay(150)
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    const items = status ? db.returns.filter((item) => item.status === status) : db.returns
    return paged(items, url)
  }),

  http.post(`${BASE}/sellers/returns/:returnId/approve`, async ({ params }) => {
    await delay(400)
    const target = db.returns.find((item) => item.id === String(params.returnId))
    if (!target) return fail(404, 'RESOURCE_NOT_FOUND', '반품 건을 찾을 수 없습니다.')
    target.status = 'APPROVED'
    return ok(target)
  }),

  http.post(`${BASE}/sellers/returns/:returnId/reject`, async ({ params }) => {
    await delay(400)
    const target = db.returns.find((item) => item.id === String(params.returnId))
    if (!target) return fail(404, 'RESOURCE_NOT_FOUND', '반품 건을 찾을 수 없습니다.')
    target.status = 'REJECTED'
    return ok(target)
  }),

  http.post(`${BASE}/sellers/returns/:returnId/pickup-complete`, async ({ params }) => {
    await delay(400)
    const target = db.returns.find((item) => item.id === String(params.returnId))
    if (!target) return fail(404, 'RESOURCE_NOT_FOUND', '반품 건을 찾을 수 없습니다.')
    target.status = 'PICKED_UP'
    return ok(target)
  }),

  /* ---------- 판매자 상품 ---------- */
  http.get(`${BASE}/sellers/products`, async ({ request }) => {
    await delay(150)
    return paged(db.products, new URL(request.url))
  }),
  http.get(`${BASE}/sellers/products/:productId`, async ({ params }) => {
    await delay(150)
    const found = db.products.find((item) => item.id === String(params.productId))
    if (found === undefined) return fail(404, 'RESOURCE_NOT_FOUND', '상품을 찾을 수 없습니다.')
    return ok(found)
  }),

  /**
   * 등록·수정은 multipart 입니다. product 파트의 JSON 을 읽고 images 파트는
   * 개수만 세어 미리보기 URL 자리를 만듭니다 (목에는 저장소가 없습니다).
   */
  http.post(`${BASE}/sellers/products`, async ({ request }) => {
    await delay(500)
    const input = await readUpsert(request)
    const template = db.products[0]
    if (template === undefined) return fail(500, 'RESOURCE_NOT_FOUND', '시드 상품이 없습니다.')
    const created = {
      ...template,
      ...input,
      id: String(++db.nextId),
      linkedGroupBuyId: null,
      linkedGroupBuyStatus: null,
      createdAt: new Date().toISOString(),
    }
    db.products.unshift(created)
    return ok(created)
  }),

  http.put(`${BASE}/sellers/products/:productId`, async ({ params, request }) => {
    await delay(400)
    const index = db.products.findIndex((item) => item.id === String(params.productId))
    const current = db.products[index]
    if (current === undefined) return fail(404, 'RESOURCE_NOT_FOUND', '상품을 찾을 수 없습니다.')
    // 공동구매가 걸린 상품은 서버가 막습니다 (명세서 3장).
    if (current.linkedGroupBuyId !== null) {
      return fail(409, 'PRODUCT_LOCKED', '공동구매가 연결된 상품은 수정할 수 없습니다.')
    }
    const updated = { ...current, ...(await readUpsert(request)) }
    db.products[index] = updated
    return ok(updated)
  }),

  http.delete(`${BASE}/sellers/products/:productId`, async ({ params }) => {
    await delay(300)
    const index = db.products.findIndex((item) => item.id === String(params.productId))
    const current = db.products[index]
    if (current === undefined) return fail(404, 'RESOURCE_NOT_FOUND', '상품을 찾을 수 없습니다.')
    if (current.linkedGroupBuyId !== null) {
      return fail(409, 'PRODUCT_LOCKED', '공동구매가 연결된 상품은 삭제할 수 없습니다.')
    }
    db.products.splice(index, 1)
    return ok(null)
  }),

  http.get(`${BASE}/withdrawal-restriction-reasons`, () =>
    ok([
      {
        id: '1',
        code: 'CUSTOM_MADE',
        name: '주문 제작 상품',
        description: '주문 제작 상품 · 개봉 시 재판매 불가',
        requiresEvidence: true,
      },
      {
        id: '2',
        code: 'SEALED_MEDIA',
        name: '복제 가능한 디지털 콘텐츠',
        description: '복제 가능한 디지털 콘텐츠 · 포장 훼손',
        requiresEvidence: false,
      },
      {
        id: '3',
        code: 'PERISHABLE',
        name: '신선식품',
        description: '신선식품 등 재판매가 곤란한 상품',
        requiresEvidence: true,
      },
    ]),
  ),

  /** AI-01. 동기 호출로 결과만 돌려주고 저장하지 않습니다. */
  http.post(`${BASE}/sellers/products/ai-extraction`, async () => {
    await delay(1400)
    const extraction: AiExtraction = {
      status: 'COMPLETED',
      fields: {
        name: { value: '무접점 기계식 키보드', confidence: 0.96 },
        brand: { value: 'KEY WORKS', confidence: 0.91 },
        categoryId: { value: '11', confidence: 0.84 },
        modelName: { value: 'KW-87', confidence: 0.77 },
        color: { value: '화이트', confidence: 0.93 },
        price: { value: 2_975_000, confidence: 0.72 },
        description: {
          value: '정전용량 무접점 방식 87키 텐키리스 키보드입니다. 무선·유선 겸용입니다.',
          confidence: 0.81,
        },
      },
      warnings: ['가격은 판매자가 최종 확인해야 합니다.'],
    }
    return ok(extraction)
  }),

  /* ---------- 판매자 공동구매 ---------- */
  http.get(`${BASE}/sellers/group-buys`, async ({ request }) => {
    await delay(150)
    const items = [
      {
        id: '1024',
        productId: '301',
        productName: 'MacBook Air M4 16GB',
        status: 'RECRUITING' as const,
        targetCount: 10,
        paidCount: 7,
        confirmedCount: 0,
        productPrice: 10_400_000,
        unitShareAmount: 1_040_000,
        shippingFee: 4_000,
        recruitEndAt: iso(3.5 * 86_400_000),
        invoiceRegisteredRate: null,
      },
      {
        id: '1008',
        productId: '308',
        productName: '게이밍 마우스패드 XL',
        status: 'FULFILLED' as const,
        targetCount: 40,
        paidCount: 40,
        confirmedCount: 38,
        productPrice: 996_000,
        unitShareAmount: 24_900,
        shippingFee: 4_000,
        recruitEndAt: iso(-18 * 86_400_000),
        invoiceRegisteredRate: 1,
      },
      {
        id: '0952',
        productId: '309',
        productName: 'USB-C 충전기 65W',
        status: 'FULFILLED' as const,
        targetCount: 25,
        paidCount: 25,
        confirmedCount: 25,
        productPrice: 800_000,
        unitShareAmount: 32_000,
        shippingFee: 4_000,
        recruitEndAt: iso(-41 * 86_400_000),
        invoiceRegisteredRate: 1,
      },
    ]
    return paged(items, new URL(request.url))
  }),

  http.post(`${BASE}/sellers/group-buys/preview`, async ({ request }) => {
    await delay(120)
    const body = (await request.json()) as {
      productPrice: number
      targetCount: number
      shippingFee: number
    }
    const breakdown = calculateAmounts(body)
    const preview: AmountPreview = {
      productPrice: body.productPrice,
      targetCount: body.targetCount,
      unitShareAmount: breakdown.unitShareAmount,
      roundingSurplus: breakdown.roundingSurplus,
      shippingFee: body.shippingFee,
      platformFeeRate: breakdown.platformFeeRate,
      estimatedPlatformFee: breakdown.estimatedPlatformFee,
      estimatedSettlementAmount: breakdown.estimatedSettlementAmount,
      feePolicy: 'RATE',
    }
    return ok(preview)
  }),

  http.post(`${BASE}/sellers/group-buys`, async () => {
    await delay(600)
    return ok({ id: String(++db.nextId) })
  }),

  http.get(`${BASE}/sellers/group-buys/:groupBuyId/deliveries`, async () => {
    await delay(200)
    const recipients = ['김민수', '이수연', '박정훈', '정하원', '최유진', '한지호', '오세라']
    const items = recipients.map((recipientName, index) => ({
      id: `D-${String(index + 1)}`,
      participationId: String(810 + index),
      recipientName,
      recipientPhone: '01012345678',
      zipcode: '34141',
      address1: '대전광역시 유성구 대학로 291',
      address2: `10${String(index + 1)}동 100${String(index + 1)}호`,
      status: index < 3 ? ('SHIPPING' as const) : ('READY' as const),
      carrierCode: index < 3 ? 'CJ' : null,
      carrierName: index < 3 ? 'CJ대한통운' : null,
      trackingNumber: index < 3 ? `12345678901${String(index)}` : null,
      shippedAt: index < 3 ? iso(-2 * 86_400_000) : null,
      deliveredAt: null,
    }))
    return ok({
      items,
      invoiceRegisteredRate: 3 / 7,
      totalCount: 7,
      registeredCount: 3,
    })
  }),

  http.put(`${BASE}/sellers/deliveries/:deliveryId/invoice`, async () => {
    await delay(400)
    return ok(null)
  }),

  /* ---------- 정산 ---------- */
  http.get(`${BASE}/sellers/settlements`, async ({ request }) => {
    await delay(150)
    return paged(db.settlements, new URL(request.url))
  }),

  http.get(`${BASE}/sellers/settlements/:settlementId`, async ({ params }) => {
    await delay(200)
    const summary = db.settlements.find((item) => item.settlementId === String(params.settlementId))
    if (!summary) return fail(404, 'RESOURCE_NOT_FOUND', '정산 건을 찾을 수 없습니다.')

    const unitShare = summary.groupBuyId === '1008' ? 24_900 : 32_000
    const shareAmountTotal = unitShare * summary.confirmedCount
    const shippingFeeTotal = 4_000 * summary.confirmedCount
    const platformFee = Math.floor(shareAmountTotal * POLICY_DEFAULTS.platformFeeRate)

    const detail: SettlementDetail = {
      ...summary,
      sellerId: '201',
      shareAmountTotal,
      shippingFeeTotal,
      roundingSurplus: 0,
      platformFeeRate: POLICY_DEFAULTS.platformFeeRate,
      platformFee,
      holdReason: null,
      items: Array.from({ length: summary.paidCount }, (_, index) => {
        const included = index < summary.confirmedCount
        return included
          ? { participationId: String(801 + index), included: true, amount: unitShare + 4_000 }
          : { participationId: String(801 + index), included: false, excludedReason: 'RETURNED' }
      }),
    }
    return ok(detail)
  }),

  http.get(`${BASE}/sellers/me/penalties`, () =>
    ok({ confirmedCount: 0, suspended: false, items: [] }),
  ),

  /* ---------- 알림 ---------- */
  http.get(`${BASE}/notifications/unread-count`, () =>
    ok({ count: db.notifications.filter((item) => item.readAt === null).length }),
  ),
  http.get(`${BASE}/notifications`, async ({ request }) => {
    await delay(150)
    // 최신순으로 내려줍니다.
    const items = [...db.notifications].sort(
      (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt),
    )
    return paged(items, new URL(request.url))
  }),
  http.post(`${BASE}/notifications/:notificationId/read`, async ({ params }) => {
    await delay(120)
    const index = db.notifications.findIndex((item) => item.id === String(params.notificationId))
    const current = db.notifications[index]
    if (current === undefined) return fail(404, 'RESOURCE_NOT_FOUND', '알림을 찾을 수 없습니다.')
    // 이미 읽은 알림은 시각을 덮어쓰지 않습니다.
    if (current.readAt === null) {
      db.notifications[index] = { ...current, readAt: new Date().toISOString() }
    }
    return ok(null)
  }),
]
