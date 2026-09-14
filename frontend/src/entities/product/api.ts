import { api } from '@/shared/api/client'
import type { Id } from '@/shared/api/types'
import type { Paged } from '@/shared/api/types'

import type {
  AiExtraction,
  Product,
  ProductInput,
  ProductListParams,
  WithdrawalRestrictionReason,
} from './model'

interface ProductApiResponse {
  productId: Id
  name: string
  brand: string | null
  modelName: string | null
  color: string | null
  categoryId: Id
  categoryName: string
  price: number
  description: string | null
  images: { imageUrl: string }[]
  aiExtracted: boolean
  withdrawalRestrictionReasonId: Id | null
  withdrawalRestrictionReason: string | null
  restrictionReviewStatus: Product['restrictionReviewStatus']
  restrictionEvidenceUrl: string | null
  linkedGroupBuyId: Id | null
  linkedGroupBuyStatus: string | null
  createdAt: string
}

function toProduct(response: ProductApiResponse): Product {
  return {
    id: response.productId,
    name: response.name,
    brand: response.brand,
    modelName: response.modelName,
    color: response.color,
    categoryId: response.categoryId,
    categoryName: response.categoryName,
    price: response.price,
    description: response.description ?? '',
    images: response.images.map((image) => image.imageUrl),
    aiExtracted: response.aiExtracted,
    withdrawalRestrictionReasonId: response.withdrawalRestrictionReasonId,
    withdrawalRestrictionReason: response.withdrawalRestrictionReason,
    restrictionReviewStatus: response.restrictionReviewStatus,
    restrictionEvidenceUrl: response.restrictionEvidenceUrl,
    linkedGroupBuyId: response.linkedGroupBuyId,
    linkedGroupBuyStatus: response.linkedGroupBuyStatus,
    createdAt: response.createdAt,
  }
}

/**
 * 등록·수정 요청 본문.
 * 백엔드는 JSON 을 `product` 파트로, 파일을 `images` 파트로 받습니다
 * (`@RequestPart`, multipart/form-data). JSON 파트는 Content-Type 을 명시해야
 * 스프링이 객체로 변환합니다.
 */
function upsertForm(input: ProductInput, images: readonly File[]): FormData {
  const form = new FormData()
  form.append('product', new Blob([JSON.stringify(input)], { type: 'application/json' }))
  for (const image of images) form.append('images', image)
  return form
}

/** 상품·AI API (PRD-01 ~ AI-01, WDR-01) */
export const productApi = {
  /** PRD-01 */
  create: async (input: ProductInput, images: readonly File[]) =>
    toProduct(await api.post<ProductApiResponse>('/sellers/products', upsertForm(input, images))),
  /** PRD-02 */
  list: async (params: ProductListParams = {}): Promise<Paged<Product>> => {
    const page = await api.getPage<ProductApiResponse>('/sellers/products', {
      query: { ...params },
    })
    return { ...page, items: page.items.map(toProduct) }
  },
  /** PRD-03 */
  detail: async (productId: Id) =>
    toProduct(await api.get<ProductApiResponse>(`/sellers/products/${productId}`)),
  /** PRD-04. 이미지를 새로 고르지 않았으면 images 파트를 비워 보냅니다. */
  update: async (productId: Id, input: ProductInput, images: readonly File[] = []) =>
    toProduct(
      await api.patch<ProductApiResponse>(
        `/sellers/products/${productId}`,
        upsertForm(input, images),
      ),
    ),
  /** PRD-05 */
  remove: (productId: Id) => api.delete(`/sellers/products/${productId}`),

  /**
   * AI-01 이미지 기반 상품 정보 추출.
   * 동기 호출이고 결과는 저장되지 않습니다. 판매자가 검수한 뒤 PRD-01 로 등록해야 합니다.
   */
  extract: (images: File[]) => {
    const form = new FormData()
    for (const image of images) form.append('images', image)
    return api.post<AiExtraction>('/sellers/products/ai-extraction', form)
  },

  /** WDR-01 */
  withdrawalReasons: () =>
    api.get<WithdrawalRestrictionReason[]>('/withdrawal-restriction-reasons'),
} as const
