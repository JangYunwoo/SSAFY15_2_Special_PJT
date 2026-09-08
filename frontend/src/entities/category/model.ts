import type { Id } from '@/shared/api/types'

/** CAT-01 카테고리 트리 */
export interface Category {
  id: Id
  name: string
  children: Category[]
}
