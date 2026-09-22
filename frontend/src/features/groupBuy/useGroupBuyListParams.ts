import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'

import {
  GROUP_BUY_SORT,
  type GroupBuyListParams,
  type GroupBuySort,
} from '@/entities/groupBuy/model'
import { scrollToListTop, toPageIndex } from '@/shared/lib/hooks/usePageParam'

// 마감 임박순이 기본입니다 (S15P21B205-227). 바꾸면 GROUP_BUY_SORT 의 첫 항목도 함께 맞추세요.
const DEFAULT_SORT: GroupBuySort = 'deadline'
export const ALL_CATEGORIES = 'all'

function isSort(value: string | null): value is GroupBuySort {
  return value !== null && (GROUP_BUY_SORT as readonly string[]).includes(value)
}

/**
 * 목록 조건을 URL 쿼리스트링에 둡니다.
 * 새로고침·뒤로가기·링크 공유에서 같은 목록이 나오게 하려는 의도이고,
 * 컴포넌트 state 로 들고 있으면 이 세 가지가 모두 깨집니다.
 */
export function useGroupBuyListParams() {
  const [searchParams, setSearchParams] = useSearchParams()

  const keyword = searchParams.get('keyword') ?? ''
  const categoryId = searchParams.get('categoryId') ?? ALL_CATEGORIES
  const sortParam = searchParams.get('sort')
  const sort = isSort(sortParam) ? sortParam : DEFAULT_SORT
  const page = toPageIndex(searchParams.get('page'))

  /** API 로 보낼 형태. 빈 값은 아예 넣지 않습니다. */
  const queryParams = useMemo<GroupBuyListParams>(
    () => ({
      ...(keyword ? { keyword } : {}),
      ...(categoryId === ALL_CATEGORIES ? {} : { categoryId }),
      sort,
      status: 'RECRUITING',
      page,
    }),
    [keyword, categoryId, sort, page],
  )

  const patch = useCallback(
    (next: Partial<{ keyword: string; categoryId: string; sort: GroupBuySort }>) => {
      setSearchParams(
        (current) => {
          const draft = new URLSearchParams(current)
          for (const [key, value] of Object.entries(next)) {
            // 기본값은 URL 을 지저분하게 만들지 않도록 지웁니다.
            const isDefault =
              value === '' ||
              (key === 'categoryId' && value === ALL_CATEGORIES) ||
              (key === 'sort' && value === DEFAULT_SORT)
            if (isDefault) draft.delete(key)
            else draft.set(key, value)
          }
          // 조건이 바뀌면 결과가 줄어 현재 쪽이 비어버릴 수 있어 첫 쪽으로 돌아갑니다.
          draft.delete('page')
          return draft
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  /** `index` 는 0부터 세는 API 기준 값입니다. */
  const goToPage = useCallback(
    (index: number) => {
      setSearchParams((current) => {
        const draft = new URLSearchParams(current)
        if (index <= 0) draft.delete('page')
        else draft.set('page', String(index + 1))
        return draft
      })
      scrollToListTop()
    },
    [setSearchParams],
  )

  return { keyword, categoryId, sort, page, queryParams, patch, goToPage }
}
