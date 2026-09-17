import { useEffect } from 'react'

/**
 * 화면이 떠 있는 동안 body 에 클래스를 붙입니다.
 *
 * 배경처럼 화면 전체를 덮어야 하는 것은 라우트 컴포넌트 안에서 처리할 수
 * 없습니다. RootLayout 이 <Outlet /> 을 감싸는 요소 없이 바로 두고, 각
 * 화면의 <main class="wrap"> 은 폭이 묶여 있어 좌우 끝까지 칠해지지
 * 않기 때문입니다.
 *
 * 화면을 떠날 때 반드시 떼어냅니다. 안 그러면 다른 화면까지 배경이 남습니다.
 */
export function useBodyClass(className: string): void {
  useEffect(() => {
    document.body.classList.add(className)
    return () => {
      document.body.classList.remove(className)
    }
  }, [className])
}
