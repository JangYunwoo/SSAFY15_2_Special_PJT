import type { AuthTokens, LoginRequest } from '@/entities/auth/model'
import type { ReturnListParams, ReturnRequestDetail, ReturnRequestSummary } from '@/entities/returnRequest/model'
import type { Penalty, Settlement, SettlementDetail, SettlementListParams } from '@/entities/settlement/model'
import { api, newIdempotencyKey } from '@/shared/api/client'
import type { Id, Krw, PageParams, Paged } from '@/shared/api/types'

import type {
  AuditLog,
  EscrowSummary,
  ExternalApiLog,
  GroupBuyJudgement,
  PenaltyDetail,
  PenaltyListParams,
  Policy,
  Refund,
  RefundListParams,
  RestrictionDecision,
  RestrictionProduct,
  ReturnDecision,
} from './model'

/**
 * 심사 대기 상품 응답.
 *
 * 백엔드는 `ProductResponse` 를 그대로 돌려줍니다 — 이미지·카테고리 ID 까지 붙은
 * 큰 객체지만 심사 화면에서 쓰는 건 일부뿐이라 필요한 필드만 추려 받습니다.
 * 판매자 상품 화면(`entities/product`)과 키 이름이 달라(`productId` vs `id`)
 * 그 모델을 그대로 재사용하지 않았습니다.
 */
interface RestrictionProductResponse {
  productId: Id
  sellerId: Id
  name: string
  categoryName: string
  price: Krw
  withdrawalRestrictionReason: string | null
  restrictionEvidenceUrl: string | null
  restrictionReviewStatus: string
  createdAt: string | null
}

function toRestrictionProduct(response: RestrictionProductResponse): RestrictionProduct {
  return {
    productId: response.productId,
    sellerId: response.sellerId,
    name: response.name,
    categoryName: response.categoryName,
    price: response.price,
    withdrawalRestrictionReason: response.withdrawalRestrictionReason,
    restrictionEvidenceUrl: response.restrictionEvidenceUrl,
    restrictionReviewStatus: response.restrictionReviewStatus,
    createdAt: response.createdAt,
  }
}

/**
 * 관리자 운영 API.
 *
 * 로그인(`/auth/admins/login`)만 열려 있고 나머지 `/admin/**` 은 ROLE_ADMIN 토큰이
 * 있어야 합니다. 관리자 토큰은 `admins.admin_id` 를 주체로 하므로 일반 사용자
 * 토큰으로는 전부 403 입니다.
 */
export const adminApi = {
  /** AUTH-05 관리자 로그인. 응답 봉투는 통합 로그인(AUTH-02)과 같습니다. */
  login: (body: LoginRequest) =>
    api.post<AuthTokens>('/auth/admins/login', body, { schema: 'authTokens' }),

  /* ---------- ESC-03 ---------- */

  /** 플랫폼 보관액·정산 대기액 집계. 대시보드의 유일한 금액 출처입니다. */
  escrowSummary: () => api.get<EscrowSummary>('/admin/escrows/summary'),

  /* ---------- STL-03 ~ STL-07 ---------- */

  /** STL-03 전체 정산 목록. 판매자 목록(STL-01)과 달리 소유권 필터가 없습니다. */
  settlements: (params: SettlementListParams = {}) =>
    api.getPage<Settlement>('/admin/settlements', { query: { ...params } }),

  settlement: (settlementId: Id) =>
    api.get<SettlementDetail>(`/admin/settlements/${settlementId}`),

  /**
   * STL-04 정산 계좌이체 실행.
   *
   * 이체가 실패해도 200 입니다. 응답의 `transferStatus` 로 결과를 판단하세요 —
   * 실패는 오류가 아니라 "재처리 대상" 이라는 상태입니다.
   */
  executeSettlement: (settlementId: Id) =>
    api.post<Settlement>(`/admin/settlements/${settlementId}/execute`),

  /** STL-05 분쟁 사유 정산 보류. 사유는 필수입니다. */
  holdSettlement: (settlementId: Id, reason: string) =>
    api.post<Settlement>(`/admin/settlements/${settlementId}/hold`, { reason }),

  /** STL-06 보류 해제. 다시 실행 대기 상태가 됩니다. */
  releaseSettlementHold: (settlementId: Id) =>
    api.post<Settlement>(`/admin/settlements/${settlementId}/release-hold`),

  /** STL-07 실패 정산 재처리. 동작은 실행과 같습니다. */
  retrySettlement: (settlementId: Id) =>
    api.post<Settlement>(`/admin/settlements/${settlementId}/retry`),

  /* ---------- RFD-03 · RFD-04 ---------- */

  /** RFD-03 실패 포함 전체 환불 목록. */
  refunds: (params: RefundListParams = {}) =>
    api.getPage<Refund>('/admin/refunds', { query: { ...params } }),

  refund: (refundId: Id) => api.get<Refund>(`/admin/refunds/${refundId}`),

  /**
   * RFD-04 실패 환불 재처리.
   *
   * `Idempotency-Key` 가 **필수**입니다(명세 11장). 재처리는 돈을 다시 내보내는
   * 동작이라, 버튼을 두 번 누르거나 응답이 늦어 다시 요청하면 이중 지급이 됩니다.
   * 호출마다 새 키를 만들면 재시도 보호가 되지 않으므로, 화면에서 한 번 만든 키를
   * 넘겨 같은 시도에는 같은 키가 가도록 합니다.
   */
  retryRefund: (refundId: Id, idempotencyKey: string = newIdempotencyKey()) =>
    api.post<Refund>(`/admin/refunds/${refundId}/retry`, undefined, { idempotencyKey }),

  /* ---------- 관리자 반품 검토 ---------- */

  /**
   * 관리자 검토가 필요한 반품 목록.
   * 구매확정 후 접수됐거나 판매자 거부에 구매자가 이의를 제기한 건입니다.
   */
  returnReviews: (params: ReturnListParams = {}): Promise<Paged<ReturnRequestSummary>> =>
    api.getPage<ReturnRequestSummary>('/admin/returns/reviews', { query: { ...params } }),

  /** 반품 승인·기각. 기각(`REJECT`)에는 사유가 필수입니다. */
  resolveReturn: (returnId: Id, decision: ReturnDecision, reason: string | null) =>
    api.post<ReturnRequestDetail>(`/admin/returns/${returnId}/resolve`, {
      decision,
      ...(reason === null || reason === '' ? {} : { reason }),
    }),

  /* ---------- PEN-02 ~ PEN-06 ---------- */

  /** PEN-02 위반 후보·확정·기각 목록. */
  penalties: (params: PenaltyListParams = {}) =>
    api.getPage<Penalty>('/admin/penalties', { query: { ...params } }),

  /** PEN-03 근거 조회. 같은 판매자의 확정 이력이 함께 옵니다. */
  penalty: (penaltyId: Id) => api.get<PenaltyDetail>(`/admin/penalties/${penaltyId}`),

  /** PEN-04 위반 확정. 누적이 한도에 닿으면 판매자가 정지됩니다. */
  confirmPenalty: (penaltyId: Id, memo: string) =>
    api.post<Penalty>(`/admin/penalties/${penaltyId}/confirm`, { memo }),

  /** PEN-05 위반 후보 기각. */
  dismissPenalty: (penaltyId: Id, memo: string) =>
    api.post<Penalty>(`/admin/penalties/${penaltyId}/dismiss`, { memo }),

  /** PEN-06 확정 취소. 누적이 한도 아래로 내려가면 정지도 풀립니다. */
  revokePenalty: (penaltyId: Id, memo: string) =>
    api.post<Penalty>(`/admin/penalties/${penaltyId}/revoke`, { memo }),

  /* ---------- 청약철회 제한 심사 ---------- */

  /** 심사 대기 상품. 페이지 없이 전체를 한 번에 돌려줍니다. */
  restrictionReviews: async (): Promise<RestrictionProduct[]> => {
    const items = await api.get<RestrictionProductResponse[]>(
      '/admin/products/withdrawal-restrictions',
    )
    return items.map(toRestrictionProduct)
  },

  reviewRestriction: async (productId: Id, status: RestrictionDecision) =>
    toRestrictionProduct(
      await api.post<RestrictionProductResponse>(
        `/admin/products/${productId}/withdrawal-restriction/review`,
        { status },
      ),
    ),

  /* ---------- ADM-03 · ADM-04 ---------- */

  /** ADM-03 정책값 전체 조회. 페이지가 아니라 전체 목록입니다. */
  policies: () => api.get<Policy[]>('/admin/policies'),

  /** ADM-04 정책값 변경. 변경 사실이 감사 로그에 남습니다. */
  updatePolicy: (policyKey: string, value: string) =>
    api.patch<Policy>(`/admin/policies/${policyKey}`, { value }),

  /* ---------- ADM-05 · ADM-07 ---------- */

  /** ADM-05 외부 금융 API 호출 이력. 최신순입니다. */
  externalApiLogs: (params: PageParams = {}) =>
    api.getPage<ExternalApiLog>('/admin/external-api-logs', { query: { ...params } }),

  /** ADM-07 관리자 조치 감사 로그. 최신순입니다. */
  auditLogs: (params: PageParams = {}) =>
    api.getPage<AuditLog>('/admin/audit-logs', { query: { ...params } }),

  /* ---------- 운영 배치 ---------- */

  /**
   * 모집 마감 판정 배치 수동 실행.
   *
   * 평소에는 스케줄러가 돌립니다. 데모나 장애 복구처럼 지금 당장 판정이
   * 필요할 때만 씁니다 — 모집 성사·실패가 확정되고 실패 건은 환불이 시작됩니다.
   */
  runRecruitmentJob: () =>
    api.post<GroupBuyJudgement>('/admin/jobs/group-buy-recruitment/run'),
} as const
