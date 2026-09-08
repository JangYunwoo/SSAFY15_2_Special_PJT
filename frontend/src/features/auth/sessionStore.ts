import { create } from 'zustand'
import { persist } from 'zustand/middleware'

import { configureAuthBridge } from '@/shared/api/client'
import type { Role } from '@/shared/api/types'
import type { Actor, AuthTokens } from '@/entities/auth/model'

interface SessionState {
  accessToken: string | null
  refreshToken: string | null
  actor: Actor | null
  /** 판매자센터 본인인증 완료 여부 (판매자센터 진입 조건) */
  sellerVerified: boolean
  signIn: (tokens: AuthTokens) => void
  signOut: () => void
  setSellerVerified: (verified: boolean) => void
}

/**
 * 세션 저장소.
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
      sellerVerified: false,

      signIn: (tokens) => {
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          actor: tokens.actor,
        })
      },

      signOut: () => {
        set({ accessToken: null, refreshToken: null, actor: null, sellerVerified: false })
      },

      setSellerVerified: (sellerVerified) => {
        set({ sellerVerified })
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
export const useHasRole = (role: Role) => useSessionStore((state) => state.actor?.role === role)
export const useSellerVerified = () => useSessionStore((state) => state.sellerVerified)
