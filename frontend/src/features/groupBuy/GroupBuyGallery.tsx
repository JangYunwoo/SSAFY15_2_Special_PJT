import { useState } from 'react'

import { cx } from '@/shared/lib/cx'

/** 이미지가 없을 때 목업처럼 회색 자리 3칸을 보여줍니다. */
const PLACEHOLDER_COUNT = 3

interface GroupBuyGalleryProps {
  images: readonly string[]
  alt: string
}

export function GroupBuyGallery({ images, alt }: GroupBuyGalleryProps) {
  const [selected, setSelected] = useState(0)
  const count = images.length > 0 ? images.length : PLACEHOLDER_COUNT
  const current = images[selected]

  return (
    <div className="gallery">
      <div className="thumb">
        {current === undefined ? (
          `상품 이미지 ${String(selected + 1)}`
        ) : (
          <img src={current} alt={`${alt} 이미지 ${String(selected + 1)}`} />
        )}
      </div>

      {count > 1 && (
        <div className="caps">
          {Array.from({ length: count }, (_, index) => {
            const source = images[index]
            return (
              <button
                key={index}
                type="button"
                className={cx(index === selected && 'on')}
                aria-label={`이미지 ${String(index + 1)} 보기`}
                aria-current={index === selected}
                onClick={() => {
                  setSelected(index)
                }}
              >
                {source === undefined ? `이미지 ${String(index + 1)}` : <img src={source} alt="" />}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
