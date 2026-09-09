import { create } from 'zustand'
import { persist } from 'zustand/middleware'

import { configureAuthBridge } from '@/shared/api/client'
import type { Actor, AuthTokens } from '@/entities/auth/model'

interface SessionState {
  accessToken: string | null
  refreshToken: string | null
  actor: Actor | null
  /**
   * 판매자센터 이용 자격. 로그인 시 서버의 `actor.isSeller` 로 채우고,
   * 판매자 전환을 마치면 true 가 됩니다 (명세서 SEL-05).
   */
  isSeller: boolean
  signIn: (tokens: AuthTokens) => void
  signOut: () => void
  setIsSeller: (isSeller: boolean) => void
}

/**
 * 세션 저장소.
 *
 * 계정은 통합 `users` 하나이고 구매자·판매자가 별개 계정이 아닙니다.
 * 로그인하면 누구나 구매자로 쓸 수 있고, 판매자센터는 전환을 마친 계정만
 * 들어갈 수 있습니다.
 *
 * 토큰을 localStorage 에 두면 XSS 에 노출되므로, 운영에서는 refresh 토큰을
 * HttpOnly 쿠키로 내리고 access 토큰만 메모리에 두는 편이 안전합니다.
 * 지금은 백엔드 쿠키 정책이 정해지지 않아 새로고침 유지를 위해 persist 를 쓰고,
 * 쿠키 방식이 확정되면 partialize 에서 토큰을 제외하면 됩니다.
 */
export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      actor: null,
      isSeller: false,

      signIn: (tokens) => {
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          actor: tokens.actor,
          isSeller: tokens.actor.isSeller,
        })
      },

      signOut: () => {
        set({ accessToken: null, refreshToken: null, actor: null, isSeller: false })
      },

      setIsSeller: (isSeller) => {
        set({ isSeller })
      },
    }),
    { name: 'gongguhan.session' },
  ),
)

/** 스토어 밖(HTTP 클라이언트)에서 토큰을 읽을 수 있게 연결합니다. */
configureAuthBridge({
  getAccessToken: () => useSessionStore.getState().accessToken,
  onUnauthorized: () => {
    useSessionStore.getState().signOut()
  },
})

/* ---------- 선택자 ---------- */

export const useActor = () => useSessionStore((state) => state.actor)
export const useIsSignedIn = () => useSessionStore((state) => state.accessToken !== null)
export const useIsSeller = () => useSessionStore((state) => state.isSeller)
export const useIsAdmin = () => useSessionStore((state) => state.actor?.role === 'ADMIN')
