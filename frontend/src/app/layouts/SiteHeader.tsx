import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router'

import { useIsSignedIn } from '@/features/auth/sessionStore'
import { NotificationBell } from '@/features/notification/NotificationBell'
import { HeaderSearch } from '@/features/search/HeaderSearch'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'
import { cx } from '@/shared/lib/cx'
import { BrandMark } from '@/shared/ui/BrandMark'
import { HeartIcon, OrdersIcon, ProfileIcon } from '@/shared/ui/icons'

/**
 * 오른쪽 아이콘 버튼 하나.
 *
 * 글자 없이 아이콘만 둡니다. 대신 aria-label 로 이름을 남깁니다 — 화면에서 글자가
 * 사라지면 스크린리더가 읽을 것도 함께 사라져, "링크" 라고만 읽히기 때문입니다.
 * title 은 마우스 사용자를 위한 것으로, 잠시 올려두면 이름이 뜹니다.
 */
function HeaderAction({ to, label, children }: { to: string; label: string; children: ReactNode }) {
  return (
    <NavLink
      className={({ isActive }) => cx('gnb-action', isActive && 'on')}
      to={to}
      aria-label={label}
      title={label}
    >
      {children}
    </NavLink>
  )
}

/**
 * 구매자 화면 공통 헤더 (.gnb)
 *
 * 로그아웃과 판매자센터는 헤더에서 내렸습니다 (S15P21B205-228). 로그아웃은 하루에
 * 한 번 누를까 말까 한 동작인데 상시 노출돼 있었고, 판매자센터는 구매자 대다수에게
 * 해당 없는 입구라 푸터와 내 정보에만 둡니다.
 *
 * "둘러보기" 도 뺐습니다. 목적지가 로고와 똑같이 홈이라 같은 곳으로 가는 길이 헤더에
 * 두 개 있었습니다.
 *
 * 장바구니는 만들지 않습니다. 공동구매는 담아뒀다 한 번에 사는 구조가 아니라,
 * 아이콘을 두면 눌러도 할 일이 없습니다.
 */
export function SiteHeader() {
  const isSignedIn = useIsSignedIn()

  return (
    <header className="gnb">
      <div className="inner">
        <Link className="brand" to="/">
          <BrandMark />
        </Link>
        <HeaderSearch />

        <nav className="gnb-actions" aria-label="주요 메뉴">
          {isSignedIn ? (
            <>
              {/* 찜은 계정에 딸린 것이라 로그인 뒤에만 보입니다 */}
              <HeaderAction to="/likes" label="찜한 공동구매">
                <HeartIcon />
              </HeaderAction>
              <HeaderAction to="/orders" label="주문현황">
                <OrdersIcon />
              </HeaderAction>
              <HeaderAction to="/me" label="내 정보">
                <ProfileIcon />
              </HeaderAction>
              {/* 알림 API(NOTI-01~03)가 아직 없어, 붙기 전까지는 벨을 감춥니다. */}
              {BACKEND_FEATURES.notifications && <NotificationBell />}
            </>
          ) : (
            <HeaderAction to="/login" label="로그인">
              <ProfileIcon />
            </HeaderAction>
          )}
        </nav>
      </div>
    </header>
  )
}





