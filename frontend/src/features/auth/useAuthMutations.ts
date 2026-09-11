import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router'

import { authApi } from '@/entities/auth/api'
import type { LoginRequest, SignupRequest } from '@/entities/auth/model'

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

export function useLogout() {
  const signOut = useSessionStore((state) => state.signOut)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => authApi.logout(),
    // 서버 폐기가 실패해도 클라이언트 세션은 반드시 정리합니다.
    onSettled: async () => {
      signOut()
      queryClient.clear()
      await navigate('/login', { replace: true })
    },
  })
}
