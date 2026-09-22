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
