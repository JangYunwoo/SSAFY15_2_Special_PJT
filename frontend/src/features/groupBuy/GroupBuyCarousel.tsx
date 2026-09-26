import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'

import type { GroupBuySummary } from '@/entities/groupBuy/model'
import { cx } from '@/shared/lib/cx'
import { ChevronIcon } from '@/shared/ui/icons'

import { GroupBuyCard } from './GroupBuyCard'

interface DragState {
  pointerId: number
  startX: number
  startScrollLeft: number
  moved: boolean
}

/** 마우스 드래그와 터치 스와이프를 지원하는 상품 카드 가로 목록. */
export function GroupBuyCarousel({
  items,
  label,
  onItemClick,
  onItemVisible,
}: {
  items: readonly GroupBuySummary[]
  label: string
  onItemClick?: ((item: GroupBuySummary, position: number) => void) | undefined
  onItemVisible?: ((item: GroupBuySummary, position: number) => void) | undefined
}) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const dragRef = useRef<DragState | null>(null)
  const suppressClickRef = useRef(false)
  const visibleRef = useRef(new Set<string>())
  const [dragging, setDragging] = useState(false)
  /** 양 끝에 닿았는지. 닿은 쪽 버튼은 감춥니다 */
  const [atStart, setAtStart] = useState(true)
  const [atEnd, setAtEnd] = useState(true)
  /**
   * 버튼의 세로 위치(사진 한가운데).
   *
   * 카드 전체의 가운데에 두면 가격·게이지 근처로 내려옵니다. 사진 높이는 카드
   * 폭을 따라가므로(aspect-ratio:1) CSS 로는 못 적고, 재어서 넣습니다.
   */
  const [navTop, setNavTop] = useState<number | null>(null)
  const itemSignature = useMemo(() => items.map((item) => item.id).join(','), [items])

  const syncNav = useCallback(() => {
    const root = scrollRef.current
    if (root === null) return
    const max = root.scrollWidth - root.clientWidth
    // 1px 은 소수점 스크롤 위치 때문입니다. 끝까지 갔는데 버튼이 남아 있곤 했습니다
    setAtStart(root.scrollLeft <= 1)
    setAtEnd(root.scrollLeft >= max - 1)
    const thumb = root.querySelector('.thumb')
    if (thumb !== null) setNavTop(thumb.getBoundingClientRect().height / 2)
  }, [])

  useEffect(() => {
    const root = scrollRef.current
    if (root === null) return
    syncNav()
    // 창 크기가 바뀌면 카드 폭도 사진 높이도 같이 바뀝니다
    const observer = new ResizeObserver(syncNav)
    observer.observe(root)
    return () => {
      observer.disconnect()
    }
  }, [syncNav, itemSignature])

  useEffect(() => {
    visibleRef.current.clear()
  }, [itemSignature])

  useEffect(() => {
    const root = scrollRef.current
    if (root === null || onItemVisible === undefined) return

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting || entry.intersectionRatio < 0.6) continue
          const index = Number((entry.target as HTMLElement).dataset.carouselIndex)
          const item = items[index]
          if (item === undefined || visibleRef.current.has(item.id)) continue
          visibleRef.current.add(item.id)
          onItemVisible(item, index)
        }
      },
      { root, threshold: 0.6 },
    )
    root.querySelectorAll<HTMLElement>('[data-carousel-index]').forEach((element) => {
      observer.observe(element)
    })
    return () => {
      observer.disconnect()
    }
  }, [itemSignature, items, onItemVisible])

  /**
   * 마우스를 누른 시점에는 아직 아무것도 잡지 않습니다.
   *
   * 예전에는 여기서 바로 setPointerCapture 를 걸었습니다. 그러면 드래그하지
   * 않고 그냥 카드를 눌러도 캡처가 살아 있는 상태로 click 이 뜨고, 브라우저는
   * click 의 대상을 캡처한 요소(이 캐러셀 div)로 바꿔버립니다. 카드의 <a> 가
   * 클릭을 받지 못해 상세 페이지로 넘어가지 않았습니다.
   *
   * 그래서 캡처는 실제로 끌기 시작한 뒤(moveDrag 의 임계값 통과)에만 겁니다.
   */
  const startDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.pointerType !== 'mouse' || event.button !== 0) return
    const root = scrollRef.current
    if (root === null) return
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startScrollLeft: root.scrollLeft,
      moved: false,
    }
    suppressClickRef.current = false
  }

  const moveDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    const root = scrollRef.current
    const drag = dragRef.current
    if (root === null || drag?.pointerId !== event.pointerId) return
    const distance = event.clientX - drag.startX
    if (Math.abs(distance) > 5) {
      // 임계값을 처음 넘은 순간에만 캡처를 잡습니다. 여기서부터는 끌기이므로
      // 포인터가 카드 밖으로 나가도 스크롤이 따라와야 하고, 뒤따르는 click 은
      // 어차피 suppressClickRef 로 막습니다.
      if (!drag.moved) {
        drag.moved = true
        suppressClickRef.current = true
        root.setPointerCapture(event.pointerId)
        setDragging(true)
      }
      event.preventDefault()
    }
    if (drag.moved) root.scrollLeft = drag.startScrollLeft - distance
  }

  const endDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    const root = scrollRef.current
    const drag = dragRef.current
    if (root === null || drag?.pointerId !== event.pointerId) return
    if (root.hasPointerCapture(event.pointerId)) root.releasePointerCapture(event.pointerId)
    dragRef.current = null
    setDragging(false)
  }


  /*
   * 좌우 버튼.
   *
   * 스크롤바를 아래에 길게 깔아두면 카드를 넘기려고 커서를 한참 아래로 내려야
   * 하고, 터치 기기에는 아예 보이지 않습니다. 끌기·스와이프는 그대로 두고
   * 버튼을 얹습니다.
   *
   * 한 번에 보이는 만큼(clientWidth) 넘깁니다. 카드 한 장씩이면 다음 넷을 보는
   * 데 네 번을 눌러야 합니다.
   */
  const scrollByPage = (direction: 1 | -1) => {
    const root = scrollRef.current
    if (root === null) return
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    root.scrollBy({ left: direction * root.clientWidth, behavior: reduced ? 'auto' : 'smooth' })
  }

  return (
    <div className="carousel">
      <div
        ref={scrollRef}
        className={cx('card-carousel', dragging && 'is-dragging')}
        role="region"
        aria-label={label}
        onScroll={syncNav}
        onPointerDown={startDrag}
        onPointerMove={moveDrag}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        onDragStart={(event) => {
          event.preventDefault()
        }}
        onClickCapture={(event) => {
          if (!suppressClickRef.current) return
          event.preventDefault()
          event.stopPropagation()
          suppressClickRef.current = false
        }}
      >
        {items.map((item, index) => (
          <div className="card-carousel-item" data-carousel-index={index} key={item.id}>
            <GroupBuyCard
              item={item}
              onClick={
                onItemClick === undefined
                  ? undefined
                  : () => {
                      onItemClick(item, index)
                    }
              }
            />
          </div>
        ))}
      </div>

      {/* 끝에 닿으면 감춥니다. 눌러도 아무 일이 없는 버튼은 고장으로 읽힙니다 */}
      {!atStart && (
        <button
          type="button"
          className="carousel-nav prev"
          style={navTop === null ? undefined : { top: navTop }}
          aria-label={`${label} 이전 상품 보기`}
          onClick={() => {
            scrollByPage(-1)
          }}
        >
          <ChevronIcon direction="prev" />
        </button>
      )}
      {!atEnd && (
        <button
          type="button"
          className="carousel-nav next"
          style={navTop === null ? undefined : { top: navTop }}
          aria-label={`${label} 다음 상품 보기`}
          onClick={() => {
            scrollByPage(1)
          }}
        >
          <ChevronIcon direction="next" />
        </button>
      )}
    </div>
  )
}



