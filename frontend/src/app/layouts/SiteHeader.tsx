import { Link, NavLink } from 'react-router'

import { useIsSignedIn } from '@/features/auth/sessionStore'
import { useLogout } from '@/features/auth/useAuthMutations'
import { NotificationBell } from '@/features/notification/NotificationBell'
import { cx } from '@/shared/lib/cx'

const NAV = [
  { to: '/', label: '공동구매', end: true },
  { to: '/about', label: '서비스 소개', end: false },
  { to: '/orders', label: '거래관리', end: false },
] as const

/** 구매자 화면 공통 헤더 (.gnb) */
export function SiteHeader() {
  const isSignedIn = useIsSignedIn()
  const logout = useLogout()

  return (
    <header className="gnb">
      <div className="inner">
        <Link className="brand" to="/">
          공구한
        </Link>
        <div className="right">
          <nav aria-label="주요 메뉴">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => cx(isActive && 'on')}
              >
                {item.label}
              </NavLink>
            ))}
            {isSignedIn && (
              <NavLink to="/me" className={({ isActive }) => cx(isActive && 'on')}>
                내 정보
              </NavLink>
            )}
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
              <NavLink to="/login" className={({ isActive }) => cx(isActive && 'on')}>
                로그인
              </NavLink>
            )}
          </nav>
          {isSignedIn && <NotificationBell />}
          <Link className="cta" to="/seller">
            판매자센터
          </Link>
        </div>
      </div>
    </header>
  )
}
