import { Navigate, Outlet, useLocation } from 'react-router'

import { useSessionStore } from '@/features/auth/sessionStore'

/**
 * 로그인만 확인하는 라우트 가드.
 * 서버가 401/403 으로 최종 판단하므로 여기서는 불필요한 요청과
 * 빈 화면 노출을 막는 정도만 합니다.
 */
export function RequireAuth() {
  const accessToken = useSessionStore((state) => state.accessToken)
  const location = useLocation()

  if (accessToken === null) {
    // 로그인 후 원래 가려던 곳으로 돌려보냅니다.
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  return <Outlet />
}

/**
 * 판매자센터는 판매자 전환을 마친 계정만 이용할 수 있습니다 (명세서 SEL-05).
 * 계정 자체는 통합이므로 역할이 아니라 `isSeller` 로 판단합니다.
 */
export function RequireSeller() {
  const isSeller = useSessionStore((state) => state.isSeller)

  if (!isSeller) return <Navigate to="/seller/verify" replace />
  return <Outlet />
}
