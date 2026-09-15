import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router'

import { adminApi } from '@/entities/admin/api'
import type { LoginRequest } from '@/entities/auth/model'
import { useSessionStore } from '@/features/auth/sessionStore'

/**
 * 가드가 넣어준 `state.from` 만 신뢰하고, 그중에서도 `/admin` 아래만 받습니다.
 * 구매자 화면에서 밀려온 경로로 관리자를 보내면 그 화면은 곧바로 403 입니다.
 */
function redirectTarget(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state) {
    const { from } = state
    if (typeof from === 'string' && from.startsWith('/admin') && !from.startsWith('//')) {
      return from
    }
  }
  return '/admin'
}

/**
 * AUTH-05 관리자 로그인.
 *
 * 세션 저장소는 구매자·판매자와 같은 것을 씁니다. 관리자 토큰의 `actor.role`
 * 이 `ADMIN` 이라 `useIsAdmin()` 으로 구분되고, 같은 브라우저에서 두 세션을
 * 동시에 유지하지는 않습니다 — 관리자로 로그인하면 구매자 세션은 덮입니다.
 * 토큰이 하나뿐인 HTTP 클라이언트 구조라, 나누려면 클라이언트부터 갈라야 합니다.
 */
export function useAdminLogin() {
  const signIn = useSessionStore((state) => state.signIn)
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (body: LoginRequest) => adminApi.login(body),
    onSuccess: async (tokens) => {
      signIn(tokens)
      // 직전 사용자의 캐시가 관리자 화면에 남지 않도록 비웁니다.
      await queryClient.resetQueries()
      await navigate(redirectTarget(location.state), { replace: true })
    },
  })
}

/**
 * 관리자 로그아웃.
 *
 * 백엔드에 토큰 폐기 엔드포인트가 없어 클라이언트 토큰만 버립니다
 * (구매자 로그아웃과 같은 상황). 운영 데이터가 캐시에 남지 않도록
 * 쿼리 캐시를 통째로 비웁니다.
 */
export function useAdminLogout() {
  const signOut = useSessionStore((state) => state.signOut)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => Promise.resolve(),
    onSettled: async () => {
      signOut()
      queryClient.clear()
      await navigate('/admin/login', { replace: true })
    },
  })
}
