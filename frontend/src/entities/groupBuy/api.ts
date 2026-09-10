import { api } from '@/shared/api/client'
import type { GroupBuyEscrow } from '@/entities/participation/model'
import type { Id, NoContent, PageParams, Paged } from '@/shared/api/types'

import type {
  AmountPreview,
  CreateGroupBuyRequest,
  GroupBuyDetail,
  GroupBuyListParams,
  GroupBuyPreviewRequest,
  GroupBuySummary,
  Participant,
  SellerGroupBuyDetail,
  SellerGroupBuySummary,
} from './model'

/** 공동구매 API (GRP-01 ~ GRP-10) */
export const groupBuyApi = {
  /** GRP-01 공개 검색·목록 */
  list: (params: GroupBuyListParams) =>
    api.getPage<GroupBuySummary>('/group-buys', { query: { ...params } }),

  /** GRP-02 상세·금액·진행률 */
  detail: (groupBuyId: Id) => api.get<GroupBuyDetail>(`/group-buys/${groupBuyId}`),

  /** GRP-03 생성 전 금액 미리보기 */
  preview: (body: GroupBuyPreviewRequest) =>
    api.post<AmountPreview>('/sellers/group-buys/preview', body),

  /** GRP-04 생성 */
  create: (body: CreateGroupBuyRequest) =>
    api.post<SellerGroupBuyDetail>('/sellers/group-buys', body),

  /** GRP-05 판매자 목록 */
  sellerList: (params: PageParams): Promise<Paged<SellerGroupBuySummary>> =>
    api.getPage<SellerGroupBuySummary>('/sellers/group-buys', { query: { ...params } }),

  /** GRP-06 판매자 운영 상세 */
  sellerDetail: (groupBuyId: Id) =>
    api.get<SellerGroupBuyDetail>(`/sellers/group-buys/${groupBuyId}`),

  /** GRP-07 참여 전 모집 조건 수정 */
  update: (groupBuyId: Id, body: Partial<CreateGroupBuyRequest>) =>
    api.patch<SellerGroupBuyDetail>(`/sellers/group-buys/${groupBuyId}`, body),

  /** GRP-08 참여자가 없는 모집 건 취소 */
  cancel: (groupBuyId: Id, reason: string) =>
    api.post<NoContent>(`/sellers/group-buys/${groupBuyId}/cancel`, { reason }),

  /** GRP-09 결제 완료 참여자 현황 */
  /** ESC-02 공동구매별 에스크로 (판매자) */
  escrows: (groupBuyId: Id) =>
    api.get<GroupBuyEscrow>(`/sellers/group-buys/${groupBuyId}/escrows`),

  participants: (groupBuyId: Id, params: PageParams = {}) =>
    api.getPage<Participant>(`/sellers/group-buys/${groupBuyId}/participants`, {
      query: { ...params },
    }),

  /** GRP-10 판매자 귀책 판매 철회. 사유가 필수입니다. */
  withdraw: (groupBuyId: Id, reason: string) =>
    api.post<NoContent>(`/sellers/group-buys/${groupBuyId}/withdrawal`, { reason }),
} as const
