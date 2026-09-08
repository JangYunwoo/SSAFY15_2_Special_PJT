import { api } from '@/shared/api/client'
import type { Id, NoContent } from '@/shared/api/types'

import type {
  DisputeRequest,
  Escrow,
  ParticipationDetail,
  ParticipationListParams,
  ParticipationSummary,
} from './model'

/** 참여·구매확정·에스크로 API (PAR-01 ~ ESC-01) */
export const participationApi = {
  /** PAR-01 */
  list: (params: ParticipationListParams = {}) =>
    api.getPage<ParticipationSummary>('/buyers/me/participations', { query: { ...params } }),

  /** PAR-02 */
  detail: (participationId: Id) =>
    api.get<ParticipationDetail>(`/buyers/me/participations/${participationId}`),

  /** PAR-03 모집 완료 전 참여 취소·환불 개시 */
  cancel: (participationId: Id) =>
    api.post<NoContent>(`/buyers/me/participations/${participationId}/cancel`),

  /** CFM-01 배송완료 건 수동 구매확정. 취소할 수 없습니다. */
  confirm: (participationId: Id) =>
    api.post<ParticipationDetail>(`/buyers/me/participations/${participationId}/confirm`, {
      confirmed: true,
    }),

  /** CFM-02 미배송·상품 불일치 이의제기 */
  dispute: (participationId: Id, body: DisputeRequest) =>
    api.post<NoContent>(`/buyers/me/participations/${participationId}/disputes`, body),

  /** ESC-01 */
  escrow: (participationId: Id) =>
    api.get<Escrow>(`/buyers/me/participations/${participationId}/escrow`),
} as const
