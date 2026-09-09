import { api } from '@/shared/api/client'
import type { Id } from '@/shared/api/types'

import type {
  AiExtraction,
  Product,
  ProductInput,
  ProductListParams,
  WithdrawalRestrictionReason,
} from './model'

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
  create: (input: ProductInput, images: readonly File[]) =>
    api.post<Product>('/sellers/products', upsertForm(input, images)),
  /** PRD-02 */
  list: (params: ProductListParams = {}) =>
    api.getPage<Product>('/sellers/products', { query: { ...params } }),
  /** PRD-03 */
  detail: (productId: Id) => api.get<Product>(`/sellers/products/${productId}`),
  /** PRD-04. 이미지를 새로 고르지 않았으면 images 파트를 비워 보냅니다. */
  update: (productId: Id, input: ProductInput, images: readonly File[] = []) =>
    api.put<Product>(`/sellers/products/${productId}`, upsertForm(input, images)),
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
