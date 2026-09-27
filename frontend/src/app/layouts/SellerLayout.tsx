import { Link, NavLink, Outlet } from 'react-router'

import { cx } from '@/shared/lib/cx'
import { BrandMark } from '@/shared/ui/BrandMark'

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
  return (
    <>
      <header className="gnb">
        <div className="inner">
          <Link className="brand" to="/seller">
            <BrandMark />
          </Link>
          <span className="badge">판매자센터</span>
          <div className="right">
            <nav aria-label="판매자 메뉴">
              <NavLink to="/about" className={({ isActive }) => cx(isActive && 'on')}>
                서비스 소개
              </NavLink>
            </nav>
            <Link className="cta" to="/seller/products/new">
              상품 등록하기
            </Link>
            {/*
              "센터 나가기" 는 구매자 화면으로 돌아가는 것이지 로그아웃이 아닙니다.
              계정은 하나뿐이고 판매자도 구매자로 계속 쓸 수 있습니다. 예전에는
              여기서 logout 을 불러 로그인 화면으로 튕겼습니다.
            */}
            <Link className="cta quiet" to="/">
              센터 나가기
            </Link>
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

      {/*
        구매자 화면과 같은 푸터를 씁니다. 판매자센터라고 다른 푸터를 쓰면 같은
        서비스 안에서 화면마다 바닥이 달라 보입니다. 결제 화면(CheckoutLayout)만
        compact 를 유지합니다 — 거기는 결제를 끝내는 자리라 링크를 줄입니다.
      */}
      <SiteFooter />
    </>
  )
}
