import type { Address } from '@/entities/address/model'

interface SavedShipping { groupBuyId: string | null; addressId: string | null; deliveryRequest: string | null }
export function initialShipping(groupBuyId: string, addresses: readonly Address[], saved: SavedShipping) {
  if (saved.groupBuyId === groupBuyId && saved.addressId !== null) {
    return {
      addressId: addresses.some((address) => address.id === saved.addressId) ? saved.addressId : '',
      deliveryRequest: saved.deliveryRequest ?? '',
    }
  }
  const initial = addresses.find((address) => address.isDefault) ?? addresses[0]
  return { addressId: initial?.id ?? '', deliveryRequest: initial?.deliveryRequest ?? '' }
}
