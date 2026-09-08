import { api } from '@/shared/api/client'

import type { Category } from './model'

export const categoryApi = {
  /** CAT-01 */
  tree: () => api.get<Category[]>('/categories'),
} as const
