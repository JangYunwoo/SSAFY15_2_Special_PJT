import { api } from '@/shared/api/client'
import type { Id, NoContent } from '@/shared/api/types'

import type {
  ReturnListParams,
  ReturnPreview,
  ReturnPreviewRequest,
  ReturnRequestDetail,
  ReturnRequestInput,
  ReturnRequestSummary,
} from './model'

/** 반품 API (RTN-01 ~ RTN-09) */
export const returnApi = {
  /** RTN-01 반품 가능 여부·예상 환불액 */
  preview: (participationId: Id, body: ReturnPreviewRequest) =>
    api.post<ReturnPreview>(`/users/me/participations/${participationId}/returns/preview`, body),

  /** RTN-02 반품 신청 */
  create: (participationId: Id, body: ReturnRequestInput) =>
    api.post<ReturnRequestDetail>(`/users/me/participations/${participationId}/returns`, body),

  /** RTN-03 */
  buyerList: (params: ReturnListParams = {}) =>
    api.getPage<ReturnRequestSummary>('/users/me/returns', { query: { ...params } }),

  /** RTN-04 */
  buyerDetail: (returnId: Id) => api.get<ReturnRequestDetail>(`/users/me/returns/${returnId}`),

  /** RTN-05 */
  sellerList: (params: ReturnListParams = {}) =>
    api.getPage<ReturnRequestSummary>('/sellers/returns', { query: { ...params } }),

  /** RTN-06 */
  approve: (returnId: Id) => api.post<ReturnRequestDetail>(`/sellers/returns/${returnId}/approve`),

  /** RTN-07 사유를 포함한 반품 거부 */
  reject: (returnId: Id, reason: string) =>
    api.post<ReturnRequestDetail>(`/sellers/returns/${returnId}/reject`, { reason }),

  /** RTN-08 수거 완료 확인·환불 개시 */
  pickupComplete: (returnId: Id) =>
    api.post<ReturnRequestDetail>(`/sellers/returns/${returnId}/pickup-complete`),

  /** RTN-09 반품 거부 이의제기 */
  dispute: (returnId: Id, detailReason: string) =>
    api.post<NoContent>(`/users/me/returns/${returnId}/disputes`, { detailReason }),
} as const
