import { Outlet, ScrollRestoration } from 'react-router'

import { SiteFooter } from './SiteFooter'
import { SiteHeader } from './SiteHeader'

/** 구매자 화면 기본 골격 */
export function RootLayout() {
  return (
    <>
      <SiteHeader />
      <Outlet />
      <SiteFooter />
      <ScrollRestoration />
    </>
  )
}
