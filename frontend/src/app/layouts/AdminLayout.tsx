import { Link, NavLink, Outlet, ScrollRestoration } from 'react-router'

import { useAdminLogout } from '@/features/admin/useAdminAuth'
import { useActor } from '@/features/auth/sessionStore'
import { cx } from '@/shared/lib/cx'
import { BrandMark } from '@/shared/ui/BrandMark'

import { SiteFooter } from './SiteFooter'

/**
 * 탭 순서는 운영자가 하루를 시작하는 순서를 따릅니다 —
 * 전체 현황을 보고(대시보드), 돈이 걸린 것부터(정산·환불), 사람 판단이
 * 필요한 것(반품·제재·상품), 마지막이 설정과 기록(정책·로그)입니다.
 */
const TABS = [
  { to: '/admin', label: '대시보드', end: true },
  { to: '/admin/settlements', label: '정산' },
  { to: '/admin/refunds', label: '환불' },
  { to: '/admin/returns', label: '반품 검토' },
  { to: '/admin/penalties', label: '판매자 제재' },
  { to: '/admin/products', label: '상품 심사' },
  { to: '/admin/policies', label: '운영 정책' },
  { to: '/admin/logs', label: '운영 로그' },
] as const

/** 관리자 콘솔 골격. 판매자센터와 같은 헤더 + 탭 구조입니다. */
export function AdminLayout() {
  const actor = useActor()
  const logout = useAdminLogout()

  return (
    <>
      <header className="gnb">
        <div className="inner">
          <Link className="brand" to="/admin">
            <BrandMark />
          </Link>
          <span className="badge">관리자 콘솔</span>
          <div className="right">
            {actor !== null && <span className="head-label">{actor.name}</span>}
            {/*
              관리자에게는 "센터 나가기" 가 없습니다. 판매자센터와 달리 같은
              계정으로 구매자 화면을 쓸 수 없고(`admins` 는 별도 테이블),
              나가는 길은 로그아웃뿐입니다.
            */}
            <button
              type="button"
              className="cta"
              disabled={logout.isPending}
              onClick={() => {
                logout.mutate()
              }}
            >
              로그아웃
            </button>
          </div>
        </div>
      </header>

      <main className="wrap">
        {/* 탭이 8개라 좁은 화면에서는 가로로 밀립니다 (.admin-tabs) */}
        <nav className="tabs admin-tabs" aria-label="관리자 메뉴">
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
