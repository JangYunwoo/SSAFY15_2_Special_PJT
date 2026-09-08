import { api } from '@/shared/api/client'
import type { Id } from '@/shared/api/types'

import type {
  AiExtraction,
  Product,
  ProductInput,
  ProductListParams,
  WithdrawalRestrictionReason,
} from './model'

/** 상품·AI API (PRD-01 ~ AI-01, WDR-01) */
export const productApi = {
  /** PRD-01 */
  create: (body: ProductInput) => api.post<Product>('/sellers/products', body),
  /** PRD-02 */
  list: (params: ProductListParams = {}) =>
    api.getPage<Product>('/sellers/products', { query: { ...params } }),
  /** PRD-03 */
  detail: (productId: Id) => api.get<Product>(`/sellers/products/${productId}`),
  /** PRD-04 */
  update: (productId: Id, body: Partial<ProductInput>) =>
    api.patch<Product>(`/sellers/products/${productId}`, body),
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
