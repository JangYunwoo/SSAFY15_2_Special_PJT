import { Navigate, Outlet, useLocation } from 'react-router'

import { useSessionStore } from '@/features/auth/sessionStore'
import type { Role } from '@/shared/api/types'

/**
 * 로그인과 역할을 확인하는 라우트 가드.
 * 서버가 401/403 으로 최종 판단하므로 여기서는 불필요한 요청과
 * 빈 화면 노출을 막는 정도만 합니다.
 */
export function RequireAuth({ role }: { role?: Role }) {
  const { accessToken, actor } = useSessionStore()
  const location = useLocation()

  if (accessToken === null) {
    // 로그인 후 원래 가려던 곳으로 돌려보냅니다.
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  if (role !== undefined && actor?.role !== role) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}

/**
 * 판매자센터는 본인인증을 마쳐야 이용할 수 있습니다.
 * (목업 seller-center/verify.html 의 진입 조건)
 */
export function RequireSellerVerified() {
  const sellerVerified = useSessionStore((state) => state.sellerVerified)

  if (!sellerVerified) return <Navigate to="/seller/verify" replace />
  return <Outlet />
}
