import { Link, NavLink } from 'react-router'

import { useIsSignedIn } from '@/features/auth/sessionStore'
import { useLogout } from '@/features/auth/useAuthMutations'
import { NotificationBell } from '@/features/notification/NotificationBell'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'
import { cx } from '@/shared/lib/cx'
import { BrandMark } from '@/shared/ui/BrandMark'

/** 로그인 여부와 무관하게 보이는 메뉴 */
const PUBLIC_NAV = [
  { to: '/', label: '공동구매', end: true },
  { to: '/about', label: '서비스 소개', end: false },
] as const

/**
 * 로그인해야 쓸 수 있는 메뉴.
 *
 * 눌러도 라우트 가드가 로그인 화면으로 돌려보낼 뿐이라 아예 감춥니다.
 * 가드는 그대로 두어 주소를 직접 입력해도 막히게 합니다.
 */
const PRIVATE_NAV = [
  { to: '/orders', label: '거래관리', end: false },
  { to: '/me', label: '내 정보', end: false },
] as const

const activeClass = ({ isActive }: { isActive: boolean }): string => cx(isActive && 'on')

/** 구매자 화면 공통 헤더 (.gnb) */
export function SiteHeader() {
  const isSignedIn = useIsSignedIn()
  const logout = useLogout()

  const menu = isSignedIn ? [...PUBLIC_NAV, ...PRIVATE_NAV] : PUBLIC_NAV

  return (
    <header className="gnb">
      <div className="inner">
        <Link className="brand" to="/">
          <BrandMark />
        </Link>
        <div className="right">
          <nav aria-label="주요 메뉴">
            {menu.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={activeClass}>
                {item.label}
              </NavLink>
            ))}

            {isSignedIn ? (
              <button
                type="button"
                onClick={() => {
                  logout.mutate()
                }}
                disabled={logout.isPending}
              >
                로그아웃
              </button>
            ) : (
              <NavLink to="/login" className={activeClass}>
                로그인
              </NavLink>
            )}
          </nav>
          {/* 알림 API(NOTI-01~03)가 아직 없어, 붙기 전까지는 벨을 감춥니다. */}
          {isSignedIn && BACKEND_FEATURES.notifications && <NotificationBell />}
          <Link className="cta" to="/seller">
            판매자센터
          </Link>
        </div>
      </div>
    </header>
  )
}
