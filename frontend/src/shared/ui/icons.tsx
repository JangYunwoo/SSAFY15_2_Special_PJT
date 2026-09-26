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


/**
 * 좌우 넘기기 꺾쇠.
 *
 * 글자(‹ ›) 대신 SVG 로 그립니다. 글자는 좌우 여백이 불균등해 원 안에서 가운데가
 * 미묘하게 어긋나고, 폰트마다 모양도 달라집니다. 상세 갤러리와 홈 캐러셀이 같이
 * 씁니다 — 같은 제품에서 "넘기기" 는 한 모양이어야 합니다.
 *
 * 선이 굵은 편(2.4)이라 위의 BASE(1.6)를 쓰지 않습니다. 원 안에 홀로 놓이는
 * 기호라 헤더 아이콘만큼 가늘면 흐릿합니다. viewBox 가 24 라 size 를 줄이면 선
 * 두께도 같은 비율로 얇아집니다 — 14px 에서는 1.4px 이라 글자 옆에 알맞습니다.
 */
export function ChevronIcon({
  direction,
  size = 26,
}: {
  direction: 'prev' | 'next'
  size?: number
}) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
      <polyline
        points={direction === 'prev' ? '15 5 8 12 15 19' : '9 5 16 12 9 19'}
        fill="none"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}



/**
 * 자동 넘김 멈춤 · 재생.
 *
 * 유니코드 ❚❚ 와 ▶ 는 폰트가 정한 크기로 그려져, 같은 font-size 를 줘도 옆의
 * 꺾쇠보다 훨씬 크고 두껍게 나왔습니다. 면으로 채우는 기호라 stroke 가 아니라
 * fill 을 씁니다.
 */
export function PauseIcon({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="currentColor" aria-hidden="true">
      <rect x="7" y="5" width="3.6" height="14" rx="1.2" />
      <rect x="13.4" y="5" width="3.6" height="14" rx="1.2" />
    </svg>
  )
}

export function PlayIcon({ size = 16 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="currentColor" aria-hidden="true">
      <path d="M8 5.5v13l11-6.5z" />
    </svg>
  )
}

