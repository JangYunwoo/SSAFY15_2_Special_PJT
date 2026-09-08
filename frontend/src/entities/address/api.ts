import { api } from '@/shared/api/client'
import type { Id } from '@/shared/api/types'

import type { Address, AddressInput } from './model'

/** 배송지 API (BUY-03 ~ BUY-06) */
export const addressApi = {
  list: () => api.get<Address[]>('/buyers/me/addresses'),
  create: (body: AddressInput) => api.post<Address>('/buyers/me/addresses', body),
  update: (addressId: Id, body: Partial<AddressInput>) =>
    api.patch<Address>(`/buyers/me/addresses/${addressId}`, body),
  remove: (addressId: Id) => api.delete(`/buyers/me/addresses/${addressId}`),
} as const
