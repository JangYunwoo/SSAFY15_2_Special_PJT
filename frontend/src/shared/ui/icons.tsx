/**
 * 헤더 아이콘.
 *
 * 아이콘 자산이 저장소에 없어 인라인 SVG 로 그립니다. 카테고리 타일과 같은
 * 규격(24 격자, 1.6px 선, 둥근 끝)이라 한 화면에서 굵기가 튀지 않습니다.
 *
 * 주문현황에 2x2 격자를 쓰지 않는 이유: 그 모양은 카테고리 줄의 '전체' 가
 * 이미 쓰고 있습니다. 같은 화면에서 한 모양이 두 뜻을 가지면 매번 헷갈립니다.
 * 대신 문서에 줄이 그어진 모양으로 "내가 뭘 했는지 기록" 을 나타냅니다.
 * 영수증 톱니는 이 크기에서 뭉개져 그냥 지저분한 선으로 보여 넣지 않았습니다.
 */

const BASE = {
  viewBox: '0 0 24 24',
  width: 27,
  height: 27,
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const

/** 주문·참여 현황 */
export function OrdersIcon() {
  return (
    <svg {...BASE}>
      <path d="M6 3h9l3 3v15H6z" />
      <polyline points="14.5 3 14.5 6.5 18 6.5" />
      <line x1="9" y1="11" x2="15" y2="11" />
      <line x1="9" y1="14.5" x2="15" y2="14.5" />
      <line x1="9" y1="18" x2="12.5" y2="18" />
    </svg>
  )
}

/** 내 정보 */
export function ProfileIcon() {
  return (
    <svg {...BASE}>
      <circle cx="12" cy="8" r="3.6" />
      <path d="M4.8 20.5c0-3.7 3.2-6.2 7.2-6.2s7.2 2.5 7.2 6.2" />
    </svg>
  )
}

/** 알림 */
export function BellIcon() {
  return (
    <svg {...BASE}>
      <path d="M18 16.5V11a6 6 0 0 0-12 0v5.5L4.5 18.5h15z" />
      <path d="M10 21.2a2.2 2.2 0 0 0 4 0" />
    </svg>
  )
}




/**
 * 찜(좋아요).
 *
 * `filled` 면 안을 채웁니다. 선만 있는 하트와 꽉 찬 하트는 모양이 같고 채움만
 * 달라, 눌렀을 때 무엇이 바뀌었는지 한눈에 읽힙니다.
 */
export function HeartIcon({ filled = false }: { filled?: boolean }) {
  return (
    <svg {...BASE} fill={filled ? 'currentColor' : 'none'}>
      <path d="M12 20.3 4.6 13a4.7 4.7 0 0 1 6.6-6.7l.8.8.8-.8A4.7 4.7 0 0 1 19.4 13z" />
    </svg>
  )
}

