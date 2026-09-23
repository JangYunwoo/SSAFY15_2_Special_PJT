import { Link, NavLink } from 'react-router'

import { useIsSignedIn } from '@/features/auth/sessionStore'
import { NotificationBell } from '@/features/notification/NotificationBell'
import { HeaderSearch } from '@/features/search/HeaderSearch'
import { BACKEND_FEATURES } from '@/shared/config/backendFeatures'
import { cx } from '@/shared/lib/cx'
import { BrandMark } from '@/shared/ui/BrandMark'

/**
 * 로그인 여부와 무관하게 보이는 메뉴.
 *
 * "공동구매" 대신 "둘러보기" 입니다 (S15P21B205-228). 서비스 이름이 공구한이고
 * 목록이 전부 공동구매라, 메뉴에 "공동구매" 라고 적어두면 무엇을 하는 버튼인지가
 * 아니라 분류 이름처럼 읽힙니다. 눌러서 할 일을 적습니다.
 *
 * "서비스 소개" 는 여기서 뺐습니다. 한 번 읽으면 다시 들어갈 일이 없는 문서라
 * 상시 노출 자리를 차지할 이유가 없고, 푸터에 같은 링크가 이미 있습니다.
 */
const PUBLIC_NAV = [{ to: '/', label: '둘러보기', end: true }] as const

/**
 * 로그인해야 쓸 수 있는 메뉴.
 *
 * 눌러도 라우트 가드가 로그인 화면으로 돌려보낼 뿐이라 아예 감춥니다.
 * 가드는 그대로 두어 주소를 직접 입력해도 막히게 합니다.
 */
const PRIVATE_NAV = [
  { to: '/orders', label: '주문/참여 현황', end: false },
  { to: '/me', label: '내 정보', end: false },
] as const

const activeClass = ({ isActive }: { isActive: boolean }): string => cx(isActive && 'on')

/**
 * 구매자 화면 공통 헤더 (.gnb)
 *
 * 로그아웃과 판매자센터를 헤더에서 내렸습니다 (S15P21B205-228).
 * 로그아웃은 하루에 한 번 누를까 말까 한 동작인데 상시 노출돼 있었고,
 * 판매자센터는 구매자 대다수에게 해당 없는 입구라 푸터 링크만 남깁니다.
 * 로그아웃은 내 정보 화면으로 옮겼습니다 — 헤더에서만 빼면 로그아웃할
 * 방법 자체가 사라집니다.
 */
export function SiteHeader() {
  const isSignedIn = useIsSignedIn()

  const menu = isSignedIn ? [...PUBLIC_NAV, ...PRIVATE_NAV] : PUBLIC_NAV

  return (
    <header className="gnb">
      <div className="inner">
        <Link className="brand" to="/">
          <BrandMark />
        </Link>
        <HeaderSearch />

        <div className="right">
          <nav aria-label="주요 메뉴">
            {menu.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={activeClass}>
                {item.label}
              </NavLink>
            ))}

            {!isSignedIn && (
              <NavLink to="/login" className={activeClass}>
                로그인
              </NavLink>
            )}
          </nav>
          {/* 알림 API(NOTI-01~03)가 아직 없어, 붙기 전까지는 벨을 감춥니다. */}
          {isSignedIn && BACKEND_FEATURES.notifications && <NotificationBell />}
        </div>
      </div>
    </header>
  )
}



