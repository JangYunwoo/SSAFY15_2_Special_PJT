/**
 * 쿼리 키를 한곳에 모아둡니다.
 * 무효화 범위를 넓게/좁게 잡는 실수를 줄이려고 계층 구조를 유지합니다.
 * 예) queryKeys.groupBuys.all → 목록 전체 무효화
 */
export const queryKeys = {
  auth: {
    all: ['auth'] as const,
    me: () => ['auth', 'me'] as const,
    sellerMe: () => ['auth', 'seller-me'] as const,
  },
  categories: {
    tree: () => ['categories', 'tree'] as const,
  },
  groupBuys: {
    all: ['group-buys'] as const,
    list: (params: unknown) => ['group-buys', 'list', params] as const,
    detail: (id: string) => ['group-buys', 'detail', id] as const,
  },
  sellerGroupBuys: {
    all: ['seller', 'group-buys'] as const,
    list: (params: unknown) => ['seller', 'group-buys', 'list', params] as const,
    detail: (id: string) => ['seller', 'group-buys', 'detail', id] as const,
    preview: (params: unknown) => ['seller', 'group-buys', 'preview', params] as const,
    participants: (id: string, params: unknown) =>
      ['seller', 'group-buys', id, 'participants', params] as const,
    deliveries: (id: string) => ['seller', 'group-buys', id, 'deliveries'] as const,
    escrows: (id: string) => ['seller', 'group-buys', id, 'escrows'] as const,
  },
  sellerProducts: {
    all: ['seller', 'products'] as const,
    list: (params: unknown) => ['seller', 'products', 'list', params] as const,
    detail: (id: string) => ['seller', 'products', 'detail', id] as const,
  },
  participations: {
    all: ['participations'] as const,
    list: (params: unknown) => ['participations', 'list', params] as const,
    detail: (id: string) => ['participations', 'detail', id] as const,
    delivery: (id: string) => ['participations', id, 'delivery'] as const,
    escrow: (id: string) => ['participations', id, 'escrow'] as const,
  },
  payments: {
    detail: (id: string) => ['payments', 'detail', id] as const,
    history: (params: unknown) => ['payments', 'history', params] as const,
    cryptoStatus: (id: string) => ['payments', id, 'crypto-status'] as const,
  },
  crypto: {
    assets: () => ['crypto', 'assets'] as const,
  },
  returns: {
    buyerList: (params: unknown) => ['returns', 'buyer', params] as const,
    buyerDetail: (id: string) => ['returns', 'buyer', 'detail', id] as const,
    sellerList: (params: unknown) => ['returns', 'seller', params] as const,
  },
  refunds: {
    list: (params: unknown) => ['refunds', 'list', params] as const,
    detail: (id: string) => ['refunds', 'detail', id] as const,
  },
  settlements: {
    all: ['settlements'] as const,
    list: (params: unknown) => ['settlements', 'list', params] as const,
    detail: (id: string) => ['settlements', 'detail', id] as const,
  },
  penalties: {
    mine: () => ['penalties', 'me'] as const,
  },
  addresses: {
    all: ['addresses'] as const,
  },
  notifications: {
    all: ['notifications'] as const,
    list: (params: unknown) => ['notifications', 'list', params] as const,
    unreadCount: () => ['notifications', 'unread-count'] as const,
  },
  withdrawalReasons: {
    active: () => ['withdrawal-restriction-reasons'] as const,
  },
  /**
   * 관리자 화면(`/admin/**`). 구매자·판매자 키와 앞자리를 분리해 둡니다 —
   * 같은 정산·환불을 보지만 범위가 달라(플랫폼 전체) 캐시를 섞으면
   * 판매자 화면에 남의 건이 그려집니다.
   */
  admin: {
    all: ['admin'] as const,
    escrowSummary: () => ['admin', 'escrow-summary'] as const,
    settlements: (params: unknown) => ['admin', 'settlements', 'list', params] as const,
    settlement: (id: string) => ['admin', 'settlements', 'detail', id] as const,
    refunds: (params: unknown) => ['admin', 'refunds', 'list', params] as const,
    refund: (id: string) => ['admin', 'refunds', 'detail', id] as const,
    returnReviews: (params: unknown) => ['admin', 'returns', 'reviews', params] as const,
    penalties: (params: unknown) => ['admin', 'penalties', 'list', params] as const,
    penalty: (id: string) => ['admin', 'penalties', 'detail', id] as const,
    restrictionReviews: () => ['admin', 'restriction-reviews'] as const,
    policies: () => ['admin', 'policies'] as const,
    externalApiLogs: (params: unknown) => ['admin', 'logs', 'external-api', params] as const,
    auditLogs: (params: unknown) => ['admin', 'logs', 'audit', params] as const,
  },
} as const
