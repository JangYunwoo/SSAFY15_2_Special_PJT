import { api } from '@/shared/api/client'
import type { Id, NoContent } from '@/shared/api/types'

import type {
  DisputeRequest,
  Escrow,
  ParticipationDetail,
  ParticipationListParams,
  ParticipationSummary,
  PurchaseConfirmResult,
} from './model'

/** 참여·구매확정·에스크로 API (PAR-01 ~ ESC-01) */
export const participationApi = {
  /** PAR-01 */
  list: (params: ParticipationListParams = {}) =>
    api.getPage<ParticipationSummary>('/users/me/participations', {
      query: { ...params },
      schema: 'participationSummary',
    }),

  /** PAR-02 */
  detail: (participationId: Id) =>
    api.get<ParticipationDetail>(`/users/me/participations/${participationId}`),

  /**
   * PAR-03 모집 완료 전 참여 취소·환불 개시.
   *
   * 본문 없이 보냅니다(백엔드 ParticipationCancelController 가 경로 변수만 받습니다).
   * 결제 완료(PAID) 이면서 모집이 끝나지 않은 건만 받아들이고, 그 외에는
   * INVALID_STATE_TRANSITION 으로 거절합니다.
   */
  cancel: (participationId: Id) =>
    api.post<NoContent>(`/users/me/participations/${participationId}/cancel`),

  /**
   * CFM-01 배송완료 건 수동 구매확정. 취소할 수 없습니다.
   *
   * 반품 제한 고지에 동의해야만 확정할 수 있어(FR-CFM) 동의 여부를 함께 보냅니다.
   * 백엔드 PurchaseConfirmRequest 의 필드명이 `noticeAgreed` 입니다.
   */
  confirm: (participationId: Id, noticeAgreed: boolean) =>
    api.post<PurchaseConfirmResult>(`/users/me/participations/${participationId}/confirm`, {
      noticeAgreed,
    }),

  /** CFM-02 미배송·상품 불일치 이의제기 */
  dispute: (participationId: Id, body: DisputeRequest) =>
    api.post<NoContent>(`/users/me/participations/${participationId}/disputes`, body),

  /** ESC-01 */
  escrow: (participationId: Id) =>
    api.get<Escrow>(`/users/me/participations/${participationId}/escrow`, {
      schema: 'escrow',
    }),
} as const
