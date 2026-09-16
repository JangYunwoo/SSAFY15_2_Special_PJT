import { api } from '@/shared/api/client'
import type { Id } from '@/shared/api/types'

import type { Address, AddressInput } from './model'

/** 배송지 API (BUY-03 ~ BUY-06) */
export const addressApi = {
  list: () => api.get<Address[]>('/users/me/addresses', { schema: 'addressList' }),
  create: (body: AddressInput) => api.post<Address>('/users/me/addresses', body),
  update: (addressId: Id, body: Partial<AddressInput>) =>
    api.patch<Address>(`/users/me/addresses/${addressId}`, body),
  remove: (addressId: Id) => api.delete(`/users/me/addresses/${addressId}`),
} as const
