import { NavLink } from 'react-router'

import { cx } from '@/shared/lib/cx'

/**
 * 거래관리 안의 2차 탭.
 *
 * 판매자센터와 같은 .tabs 스타일을 씁니다. 참여 내역에는 결제에 성공한 건만
 * 나오므로, 실패·중단된 결제는 결제 내역에서만 확인할 수 있습니다.
 */
export function OrdersTabs() {
  return (
    <nav className="tabs" aria-label="거래관리 메뉴">
      <NavLink to="/orders" end className={({ isActive }) => cx(isActive && 'on')}>
        참여 내역
      </NavLink>
      <NavLink to="/orders/payments" className={({ isActive }) => cx(isActive && 'on')}>
        결제 내역
      </NavLink>
    </nav>
  )
}
