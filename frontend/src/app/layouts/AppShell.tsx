import { Outlet, ScrollRestoration } from 'react-router'

/**
 * 모든 라우트의 바깥 껍데기. 스크롤 복원을 한 곳에서만 겁니다.
 *
 * 전에는 구매자·판매자센터·관리자 레이아웃이 각자 `ScrollRestoration` 을 달았고,
 * 결제 단계·판매자 전환·관리자 로그인처럼 그 밖에 있는 화면은 빠져 있었습니다.
 * 그래서 배송지에서 결제로 넘어가면 앞 화면에서 내린 만큼 중간에서 시작했습니다.
 * 새로 들어간 화면은 맨 위에서, 뒤로가기로 돌아온 화면은 보던 자리에서 시작합니다.
 */
export function AppShell() {
  return (
    <>
      <Outlet />
      <ScrollRestoration />
    </>
  )
}
