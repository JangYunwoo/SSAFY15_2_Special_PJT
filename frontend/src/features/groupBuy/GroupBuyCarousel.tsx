import { useEffect, useMemo, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'

import type { GroupBuySummary } from '@/entities/groupBuy/model'
import { cx } from '@/shared/lib/cx'

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
  reasons,
  onItemClick,
  onItemVisible,
}: {
  items: readonly GroupBuySummary[]
  label: string
  reasons?: Readonly<Record<string, string>> | undefined
  onItemClick?: ((item: GroupBuySummary, position: number) => void) | undefined
  onItemVisible?: ((item: GroupBuySummary, position: number) => void) | undefined
}) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const dragRef = useRef<DragState | null>(null)
  const suppressClickRef = useRef(false)
  const visibleRef = useRef(new Set<string>())
  const [dragging, setDragging] = useState(false)
  const itemSignature = useMemo(() => items.map((item) => item.id).join(','), [items])

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

  return (
    <div
      ref={scrollRef}
      className={cx('card-carousel', dragging && 'is-dragging')}
      role="region"
      aria-label={label}
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
            reason={reasons?.[item.id]}
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
  )
}
