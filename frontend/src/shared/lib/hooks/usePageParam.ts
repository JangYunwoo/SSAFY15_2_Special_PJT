import { useCallback } from 'react'
import { useSearchParams } from 'react-router'

/**
 * URL 의 `page` 를 API 가 쓰는 0부터 세는 값으로 바꿉니다.
 * 주소창은 사람이 읽는 곳이라 1부터 세고(`?page=2` = 두 번째 쪽),
 * 숫자가 아니거나 범위를 벗어나면 첫 쪽으로 봅니다.
 */
export function toPageIndex(value: string | null): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 1 ? parsed - 1 : 0
}

/** 쪽을 옮기면 목록 위에서부터 다시 봅니다. */
export function scrollToListTop(): void {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/**
 * 목록이 하나뿐인 화면에서 현재 쪽을 URL 에 두는 훅.
 * 새로고침·뒤로가기·링크 공유에서 같은 쪽이 나옵니다.
 *
 * 검색어·필터가 함께 있는 목록은 조건이 바뀔 때 쪽도 되돌려야 해서
 * 전용 훅(`useGroupBuyListParams`)을 씁니다.
 */
export function usePageParam(): { page: number; goToPage: (index: number) => void } {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = toPageIndex(searchParams.get('page'))

  const goToPage = useCallback(
    (index: number) => {
      setSearchParams((current) => {
        const draft = new URLSearchParams(current)
        // 첫 쪽은 기본값이라 URL 을 지저분하게 만들지 않습니다.
        if (index <= 0) draft.delete('page')
        else draft.set('page', String(index + 1))
        return draft
      })
      scrollToListTop()
    },
    [setSearchParams],
  )

  return { page, goToPage }
}
