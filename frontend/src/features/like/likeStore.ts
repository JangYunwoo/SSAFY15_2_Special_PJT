import { create } from 'zustand'
import { persist } from 'zustand/middleware'

import { useSessionStore } from '@/features/auth/sessionStore'

interface LikeState {
  /** 사용자 id 별 찜 목록. Set 은 JSON 으로 저장되지 않아 배열로 둡니다 */
  byUser: Record<string, string[]>
  toggle: (userId: string, groupBuyId: string) => void
}

/**
 * 찜 목록.
 *
 * 백엔드에 찜 API 가 없어 브라우저에만 저장합니다. 그래서 다른 기기에서는 보이지
 * 않고, 이 브라우저의 저장소를 비우면 사라집니다.
 *
 * 그래도 사용자 id 로 칸을 나눠 담습니다. 한 덩어리로 두면 같은 PC 를 쓰는 다음
 * 사람에게 앞사람의 찜이 그대로 보입니다. 찜은 "이 계정이 관심 있는 것" 이지
 * "이 브라우저가 관심 있는 것" 이 아닙니다.
 *
 * 서버 API 가 생기면 이 파일만 바꾸면 됩니다. 화면은 useIsLiked 와 useToggleLike
 * 만 쓰고 어디에 저장되는지는 모릅니다.
 */
const useLikeStore = create<LikeState>()(
  persist(
    (set) => ({
      byUser: {},
      toggle: (userId, groupBuyId) => {
        set((state) => {
          const mine = state.byUser[userId] ?? []
          return {
            byUser: {
              ...state.byUser,
              [userId]: mine.includes(groupBuyId)
                ? mine.filter((value) => value !== groupBuyId)
                : [...mine, groupBuyId],
            },
          }
        })
      },
    }),
    { name: 'gongguhan.likes' },
  ),
)

/** 로그인한 사용자 id. 비로그인이면 null 이라 찜을 읽지도 쓰지도 않습니다 */
const useUserId = () => useSessionStore((state) => state.actor?.id ?? null)

/**
 * 찜이 없을 때 돌려줄 빈 목록.
 *
 * 반드시 하나를 돌려써야 합니다. 셀렉터 안에서 `[]` 를 새로 만들면 호출할 때마다
 * 다른 참조가 나오고, zustand 가 useSyncExternalStore 로 스냅샷을 Object.is 로
 * 비교하므로 매번 "바뀌었다" 로 판정해 무한 리렌더에 빠집니다
 * (React error #185: Maximum update depth exceeded).
 *
 * 찜한 것이 하나도 없는 사용자에게만 터져서, 정작 "아직 찜한 상품이 없어요" 화면이
 * 보이지 않고 페이지 전체가 죽었습니다.
 */
const NO_LIKES: readonly string[] = []

/**
 * 셀렉터 본체. 훅에서 분리해 두어야 참조가 안정적인지 테스트로 확인할 수 있습니다.
 */
export function selectLikedIds(
  byUser: Record<string, string[]>,
  userId: string | null,
): readonly string[] {
  if (userId === null) return NO_LIKES
  return byUser[userId] ?? NO_LIKES
}

/** 내가 찜한 공동구매 id 목록 */
export function useLikedIds(): readonly string[] {
  const userId = useUserId()
  return useLikeStore((state) => selectLikedIds(state.byUser, userId))
}

/**
 * 이 건을 찜했는가.
 *
 * 목록 전체를 구독하면 카드 하나가 바뀔 때 화면의 카드가 전부 다시 그려집니다.
 */
export function useIsLiked(groupBuyId: string): boolean {
  const userId = useUserId()
  return useLikeStore((state) =>
    userId === null ? false : (state.byUser[userId] ?? []).includes(groupBuyId),
  )
}

/** 찜 토글. 비로그인이면 아무 일도 하지 않고 false 를 돌려줍니다 */
export function useToggleLike(): (groupBuyId: string) => boolean {
  const userId = useUserId()
  const toggle = useLikeStore((state) => state.toggle)

  return (groupBuyId) => {
    if (userId === null) return false
    toggle(userId, groupBuyId)
    return true
  }
}


