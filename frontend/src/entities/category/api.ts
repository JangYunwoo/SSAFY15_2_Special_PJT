import { z } from 'zod'

import { api } from '@/shared/api/client'
import { categorySchema } from '@/shared/api/schemas'

import type { Category } from './model'

export const categoryApi = {
  /** CAT-01 */
  tree: () => api.get<Category[]>('/categories', { schema: z.array(categorySchema) }),
} as const
