import { create } from 'zustand'
import { persist } from 'zustand/middleware'

import { authApi } from '@/entities/auth/api'
import { configureAuthBridge } from '@/shared/api/client'
import type { Actor, AuthTokens } from '@/entities/auth/model'

interface SessionState {
  accessToken: string | null
  refreshToken: string | null
  actor: Actor | null
  /**
   * 직전 세션이 만료로 끝났는지. 로그인 화면에서 이유를 알려주는 데만 씁니다.
   *
   * 스스로 로그아웃한 것과 구분해야 합니다. 둘 다 세션이 비는 것은 같지만,
   * 만료는 사용자가 하지 않은 일이라 설명이 없으면 갑자기 튕긴 것처럼 보입니다.
   */
  expired: boolean
  signIn: (tokens: AuthTokens) => void
  signOut: () => void
  /** 재발급까지 실패해 세션을 강제로 끝냅니다. */
  expireSession: () => void
  /** 만료 안내를 한 번 보여준 뒤 지웁니다. */
  clearExpired: () => void
}

/**
 * 세션 저장소.
 *
 * 계정은 통합 `users` 하나이고 구매자·판매자가 별개 계정이 아닙니다.
 * 로그인하면 누구나 구매자로 쓸 수 있고, 판매자센터는 전환을 마친 계정만
 * 들어갈 수 있습니다.
 *
 * 판매자 자격은 `actor.isSeller` 하나만 믿습니다. 백엔드가 JWT 의 `isSeller`
 * 클레임으로 ROLE_SELLER 를 부여하므로(JwtAuthenticationFilter), 클라이언트에서
 * 이 값을 세워봐야 `/sellers/**` 요청은 그대로 403 입니다. 예전에는 본인인증
 * 화면이 이 값을 직접 켜서 판매자센터에 들어간 것처럼 보였지만, 실제로는
 * 모든 요청이 실패했습니다.
 *
 * 토큰을 localStorage 에 두면 XSS 에 노출되므로, 운영에서는 refresh 토큰을
 * HttpOnly 쿠키로 내리고 access 토큰만 메모리에 두는 편이 안전합니다.
 * 지금은 백엔드 쿠키 정책이 정해지지 않아 새로고침 유지를 위해 persist 를 쓰고,
 * 쿠키 방식이 확정되면 partialize 에서 토큰을 제외하면 됩니다.
 */
export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      actor: null,
      expired: false,

      signIn: (tokens) => {
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          actor: tokens.actor,
          expired: false,
        })
      },

      signOut: () => {
        set({ accessToken: null, refreshToken: null, actor: null, expired: false })
      },

      expireSession: () => {
        // 로그인하지 않은 상태에서 받은 401 까지 만료로 알리면 엉뚱한 안내가 됩니다.
        if (get().accessToken === null) return
        set({ accessToken: null, refreshToken: null, actor: null, expired: true })
      },

      clearExpired: () => {
        set({ expired: false })
      },
    }),
    {
      name: 'gongguhan.session',
      // expired 는 이번 전환에만 쓰는 값이라 저장하지 않습니다. 남겨두면 며칠 뒤
      // 새로고침에도 "세션이 만료되었습니다" 가 다시 뜹니다.
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        actor: state.actor,
      }),
    },
  ),
)

/**
 * 스토어 밖(HTTP 클라이언트)에서 토큰을 읽고 재발급할 수 있게 연결합니다.
 *
 * 액세스 토큰은 30분(expiresIn 1800)이면 만료됩니다. 재발급이 없으면
 * 그때마다 로그아웃되므로, 401 을 만나면 refresh 토큰으로 조용히 갱신합니다.
 * AUTH-03 컨트롤러가 아직 없어 지금은 실패하고 세션이 정리되지만, 백엔드가
 * 붙는 즉시 여기 손대지 않고 그대로 동작합니다.
 */
configureAuthBridge({
  getAccessToken: () => useSessionStore.getState().accessToken,

  refresh: async () => {
    const { refreshToken, signIn } = useSessionStore.getState()
    if (refreshToken === null) return false
    try {
      // skipRefresh 로 보내므로 이 요청이 401 이어도 다시 재발급하지 않습니다.
      signIn(await authApi.refresh(refreshToken))
      return true
    } catch {
      // 재발급도 실패하면 아래 onUnauthorized 가 세션을 정리합니다.
      return false
    }
  },

  onUnauthorized: () => {
    // signOut 이 아니라 expireSession 입니다. 스스로 로그아웃한 것이 아니므로
    // 로그인 화면에서 이유를 설명해 줘야 합니다.
    useSessionStore.getState().expireSession()
  },
})

/* ---------- 선택자 ---------- */

export const useActor = () => useSessionStore((state) => state.actor)
export const useIsSignedIn = () => useSessionStore((state) => state.accessToken !== null)
/** 직전 세션이 만료로 끝났는지. 로그인 화면의 안내 문구에만 씁니다. */
export const useSessionExpired = () => useSessionStore((state) => state.expired)
/** 판매자센터 이용 자격. 서버가 내려준 actor 만 신뢰합니다 (명세서 SEL-05). */
export const useIsSeller = () => useSessionStore((state) => state.actor?.isSeller === true)
export const useIsAdmin = () => useSessionStore((state) => state.actor?.role === 'ADMIN')
