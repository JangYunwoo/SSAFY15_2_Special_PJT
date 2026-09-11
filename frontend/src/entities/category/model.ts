import type { Id } from '@/shared/api/types'

/** CAT-01 카테고리 트리 (백엔드 CategoryResponse) */
export interface Category {
  categoryId: Id
  name: string
  depth: number
  children: Category[]
}
