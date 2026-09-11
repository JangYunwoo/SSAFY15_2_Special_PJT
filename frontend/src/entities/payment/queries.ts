import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'
import type { Id } from '@/shared/api/types'

import { paymentApi } from './api'
import type { PaymentHistoryParams } from './model'

export const paymentQueries = {
  detail: (paymentId: Id) =>
    queryOptions({
      queryKey: queryKeys.payments.detail(paymentId),
      queryFn: () => paymentApi.detail(paymentId),
    }),

  history: (params: PaymentHistoryParams = {}) =>
    queryOptions({
      queryKey: queryKeys.payments.history(params),
      queryFn: () => paymentApi.history(params),
    }),

  cryptoAssets: () =>
    queryOptions({
      queryKey: queryKeys.crypto.assets(),
      queryFn: () => paymentApi.cryptoAssets(),
      staleTime: 10 * 60 * 1000,
    }),

  /** 입금·컨펌은 명세서 18장 기준 5초 주기로 조회합니다. */
  cryptoStatus: (paymentId: Id) =>
    queryOptions({
      queryKey: queryKeys.payments.cryptoStatus(paymentId),
      queryFn: () => paymentApi.cryptoStatus(paymentId),
      refetchInterval: (query) => {
        // 확정·실패로 끝나면 더 물어볼 것이 없습니다.
        const phase = query.state.data?.phase
        return phase === 'COMPLETED' || phase === 'FAILED' ? false : 5000
      },
    }),
} as const
