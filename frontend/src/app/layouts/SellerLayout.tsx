import { Link, NavLink, Outlet, ScrollRestoration } from 'react-router'

import { useLogout } from '@/features/auth/useAuthMutations'
import { cx } from '@/shared/lib/cx'

import { SiteFooter } from './SiteFooter'

const TABS = [
  { to: '/seller/products', label: '상품 관리' },
  { to: '/seller', label: '공동구매 관리', end: true },
  { to: '/seller/orders', label: '주문 관리' },
  { to: '/seller/returns', label: '반품 관리' },
  { to: '/seller/settlements', label: '정산 관리' },
] as const

/** 판매자센터 골격. 헤더 + 5개 탭 */
export function SellerLayout() {
  const logout = useLogout()

  return (
    <>
      <header className="gnb">
        <div className="inner">
          <Link className="brand" to="/seller">
            공구한
          </Link>
          <span className="badge">판매자센터</span>
          <div className="right">
            <nav aria-label="판매자 메뉴">
              <NavLink to="/about" className={({ isActive }) => cx(isActive && 'on')}>
                서비스 소개
              </NavLink>
            </nav>
            <Link className="cta fill" to="/seller/products/new">
              상품 등록하기
            </Link>
            <button
              type="button"
              className="cta"
              onClick={() => {
                logout.mutate()
              }}
            >
              센터 나가기
            </button>
          </div>
        </div>
      </header>

      <main className="wrap">
        <nav className="tabs" aria-label="판매자센터 탭">
          {TABS.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={'end' in tab ? tab.end : false}
              className={({ isActive }) => cx(isActive && 'on')}
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
        <Outlet />
      </main>

      <SiteFooter compact />
      <ScrollRestoration />
    </>
  )
}
