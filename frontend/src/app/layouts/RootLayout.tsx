import { Outlet, ScrollRestoration, useLocation } from 'react-router'

import { SiteFooter } from './SiteFooter'
import { SiteHeader } from './SiteHeader'

/**
 * 큰 푸터를 다는 화면인지 봅니다.
 *
 * 처음 온 사람이 서비스를 둘러보는 화면에만 답니다. 거기서는 "이게 뭐 하는
 * 곳인지", "약관은 어디 있는지" 를 찾아 스크롤을 내리니 푸터가 일을 합니다.
 *
 * 로그인·회원가입이나 거래관리·내 정보처럼 이미 들어와서 할 일이 정해진
 * 화면은 다릅니다. 폼을 채우거나 주문을 확인하러 온 사람에게 링크 묶음을
 * 내밀 이유가 없고, 로그인 화면에서 "판매자센터" 같은 링크는 오히려 하던
 * 일에서 벗어나게 합니다. 결제·판매자센터·관리자가 이미 compact 를 쓰는
 * 것과 같은 이유입니다.
 */
function hasFullFooter(pathname: string): boolean {
  return pathname === '/' || pathname === '/about' || pathname.startsWith('/group-buys')
}

/** 구매자 화면 기본 골격 */
export function RootLayout() {
  const { pathname } = useLocation()

  return (
    <>
      <SiteHeader />
      <Outlet />
      <SiteFooter compact={!hasFullFooter(pathname)} />
      <ScrollRestoration />
    </>
  )
}
