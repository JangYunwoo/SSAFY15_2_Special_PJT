import { Outlet, ScrollRestoration, useLocation } from 'react-router'

import { SiteFooter } from './SiteFooter'
import { SiteHeader } from './SiteHeader'

/**
 * 푸터를 아예 달지 않는 화면.
 *
 * 로그인·회원가입은 폼 하나를 채우는 것이 전부인 화면입니다. 여기서 나가는
 * 링크는 하던 일에서 벗어나게 할 뿐이라, 한 줄짜리도 두지 않고 비웁니다.
 * 둘은 오가는 화면이라 한쪽에만 푸터가 있으면 이동할 때 화면이 들썩입니다.
 *
 * 나머지 구매자 화면은 전부 같은 푸터를 씁니다. 화면마다 다르면 그때그때
 * 규칙을 다시 정해야 하고, 보는 사람도 어디에 뭐가 있는지 헷갈립니다.
 * 결제·판매자센터·관리자는 각자 레이아웃에서 compact 를 쓰고 있습니다.
 */
const NO_FOOTER_PATHS: readonly string[] = ['/login', '/signup']

/** 구매자 화면 기본 골격 */
export function RootLayout() {
  const { pathname } = useLocation()

  return (
    <>
      <SiteHeader />
      <Outlet />
      {!NO_FOOTER_PATHS.includes(pathname) && <SiteFooter />}
      <ScrollRestoration />
    </>
  )
}
