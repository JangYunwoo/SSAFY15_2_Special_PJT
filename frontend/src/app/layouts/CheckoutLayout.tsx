import { Link, Outlet } from 'react-router'

import { SiteFooter } from './SiteFooter'

/**
 * 결제 플로우 전용 골격.
 * 이탈을 줄이려고 로고만 남기고 메뉴를 감춥니다 (목업 checkout 헤더).
 */
export function CheckoutLayout() {
  return (
    <>
      <header className="gnb">
        <div className="inner">
          <Link className="brand" to="/">
            공구한
          </Link>
          <span className="head-label">· 공동구매 결제하기</span>
        </div>
      </header>
      <Outlet />
      <SiteFooter compact />
    </>
  )
}
