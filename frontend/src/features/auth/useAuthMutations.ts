import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router'

import { authApi } from '@/entities/auth/api'
import type { LoginRequest, SignupRequest } from '@/entities/auth/model'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'

import { useSessionStore } from './sessionStore'

/**
 * 가드가 넣어준 `state.from` 만 신뢰합니다.
 * 외부 주소나 프로토콜 상대 경로(`//evil.com`)로 튀지 않도록 내부 경로만 허용합니다.
 */
function redirectTarget(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state) {
    const { from } = state
    if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')) return from
  }
  return '/'
}

/**
 * AUTH-02 통합 로그인.
 *
 * 계정 종류를 고르지 않습니다. 로그인하면 누구나 구매자로 쓸 수 있고,
 * 판매자센터는 전환(SEL-05)을 마친 계정만 들어갈 수 있습니다.
 * 가드에 막혀 왔다면 원래 가려던 곳으로 돌려보냅니다.
 */
export function useLogin() {
  const signIn = useSessionStore((state) => state.signIn)
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (body: LoginRequest) => authApi.login(body),
    onSuccess: async (tokens) => {
      signIn(tokens)
      // 이전 사용자의 캐시가 남지 않도록 비웁니다.
      await queryClient.resetQueries()
      await navigate(redirectTarget(location.state), { replace: true })
    },
  })
}

/**
 * AUTH-01 통합 회원가입.
 *
 * 백엔드는 생성된 계정(`{id, email, nickname}`)만 돌려주고 토큰은 주지 않습니다.
 * 그래서 가입 직후 세션을 세울 수 없고, 로그인 화면으로 넘겨 안내합니다.
 */
export function useSignup() {
  const navigate = useNavigate()

  return useMutation({
    mutationFn: (body: SignupRequest) => authApi.signup(body),
    onSuccess: async (created) => {
      await navigate('/login', { replace: true, state: { signedUpEmail: created.email } })
    },
  })
}

/**
 * AUTH-04 로그아웃.
 *
 * 서버에 알려 리프레시 토큰을 폐기한 뒤 화면을 정리합니다. 서버에 알리지
 * 않으면 그 토큰으로 액세스 토큰을 계속 받을 수 있어, 사용자가 생각하는
 * "로그아웃"과 실제가 달라집니다.
 *
 * 정리를 onSettled 에 두는 이유: 서버 호출이 실패해도 화면에서는 반드시
 * 로그아웃돼야 합니다. 네트워크가 끊겼다고 로그인 상태로 남겨 두는 쪽이 훨씬
 * 나쁩니다. 백엔드도 로그아웃은 언제나 성공으로 처리합니다(유효하지 않은
 * 토큰이면 폐기할 것이 없으니 조용히 넘어갑니다).
 */
export function useLogout() {
  const signOut = useSessionStore((state) => state.signOut)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async () => {
      // 토큰이 없으면 보낼 것도 없습니다. 백엔드가 빈 값을 거절하기도 합니다(@NotBlank).
      const { refreshToken } = useSessionStore.getState()
      if (refreshToken === null || !BACKEND_FEATURES.logout) return
      await authApi.logout(refreshToken)
    },
    onSettled: async () => {
      signOut()
      queryClient.clear()
      await navigate('/login', { replace: true })
    },
  })
}
