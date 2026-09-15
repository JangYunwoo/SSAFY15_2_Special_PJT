import { Navigate, Outlet, useLocation } from 'react-router'

import { useIsAdmin, useIsSeller, useSessionStore } from '@/features/auth/sessionStore'

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
  const isSeller = useIsSeller()

  if (!isSeller) return <Navigate to="/seller/verify" replace />
  return <Outlet />
}

/**
 * 관리자 페이지 가드 (AUTH-05).
 *
 * 관리자는 `users` 가 아니라 `admins` 테이블의 별도 계정이라 로그인 경로도
 * 따로입니다. 그래서 `RequireAuth` 와 겹쳐 쓰지 않고 이 가드 하나로 끝냅니다 —
 * 겹쳐 두면 일반 사용자가 `/admin` 에 들어왔을 때 구매자 로그인 화면으로
 * 보내고, 거기서 로그인해도 다시 막혀 오갈 데가 없어집니다.
 *
 * 실제 차단은 서버가 합니다(`/admin/**` 은 ROLE_ADMIN). 여기서는 불필요한
 * 403 요청과 빈 화면 노출을 막는 정도만 합니다.
 */
export function RequireAdmin() {
  const isAdmin = useIsAdmin()
  const location = useLocation()

  if (!isAdmin) {
    return <Navigate to="/admin/login" state={{ from: location.pathname }} replace />
  }

  return <Outlet />
}
