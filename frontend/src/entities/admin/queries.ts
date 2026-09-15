import { queryOptions } from '@tanstack/react-query'

import type { ReturnListParams } from '@/entities/returnRequest/model'
import type { SettlementListParams } from '@/entities/settlement/model'
import { queryKeys } from '@/shared/api/queryKeys'
import type { Id, PageParams } from '@/shared/api/types'

import { adminApi } from './api'
import type { PenaltyListParams, RefundListParams } from './model'

/**
 * 관리자 화면 쿼리.
 *
 * 무효화는 `queryKeys.admin.all` 하나로 넓게 겁니다. 정산 실행이 에스크로
 * 집계를, 반품 처리가 환불 목록을 바꾸는 식으로 서로 얽혀 있어, 좁게 잡으면
 * 방금 조치한 화면 옆의 숫자가 남습니다.
 */
export const adminQueries = {
  escrowSummary: () =>
    queryOptions({
      queryKey: queryKeys.admin.escrowSummary(),
      queryFn: () => adminApi.escrowSummary(),
    }),

  settlements: (params: SettlementListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.admin.settlements(params),
      queryFn: () => adminApi.settlements(params),
    }),

  settlement: (settlementId: Id) =>
    queryOptions({
      queryKey: queryKeys.admin.settlement(settlementId),
      queryFn: () => adminApi.settlement(settlementId),
    }),

  refunds: (params: RefundListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.admin.refunds(params),
      queryFn: () => adminApi.refunds(params),
    }),

  refund: (refundId: Id) =>
    queryOptions({
      queryKey: queryKeys.admin.refund(refundId),
      queryFn: () => adminApi.refund(refundId),
    }),

  returnReviews: (params: ReturnListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.admin.returnReviews(params),
      queryFn: () => adminApi.returnReviews(params),
    }),

  penalties: (params: PenaltyListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.admin.penalties(params),
      queryFn: () => adminApi.penalties(params),
    }),

  penalty: (penaltyId: Id) =>
    queryOptions({
      queryKey: queryKeys.admin.penalty(penaltyId),
      queryFn: () => adminApi.penalty(penaltyId),
    }),

  restrictionReviews: () =>
    queryOptions({
      queryKey: queryKeys.admin.restrictionReviews(),
      queryFn: () => adminApi.restrictionReviews(),
    }),

  policies: () =>
    queryOptions({
      queryKey: queryKeys.admin.policies(),
      queryFn: () => adminApi.policies(),
    }),

  externalApiLogs: (params: PageParams = {}) =>
    queryOptions({
      queryKey: queryKeys.admin.externalApiLogs(params),
      queryFn: () => adminApi.externalApiLogs(params),
    }),

  auditLogs: (params: PageParams = {}) =>
    queryOptions({
      queryKey: queryKeys.admin.auditLogs(params),
      queryFn: () => adminApi.auditLogs(params),
    }),
} as const
