import { useState } from 'react'

import { cx } from '@/shared/lib/cx'

/** 이미지가 없을 때 목업처럼 회색 자리 3칸을 보여줍니다. */
const PLACEHOLDER_COUNT = 3

/**
 * 돋보기 배율.
 *
 * 올라오는 사진이 1200px 안팎이고 표시는 540px 정도라, 2.5배까지는 원본
 * 픽셀이 버팁니다. 더 키우면 뭉개집니다.
 */
const ZOOM = 2.5

interface GroupBuyGalleryProps {
  images: readonly string[]
  alt: string
}

/** 마우스가 사진 위 어디에 있는지 (0~100%) */
interface Spot {
  x: number
  y: number
}

export function GroupBuyGallery({ images, alt }: GroupBuyGalleryProps) {
  const [selected, setSelected] = useState(0)
  const [spot, setSpot] = useState<Spot | null>(null)
  const count = images.length > 0 ? images.length : PLACEHOLDER_COUNT
  const current = images[selected]

  /**
   * 마우스 위치를 비율로 바꿔 확대 레이어의 배경 위치로 씁니다.
   *
   * 배경을 2.5배로 키운 뒤 background-position 에 같은 비율을 주면, 커서가
   * 가리키는 지점이 그대로 가운데에 옵니다. 라이브러리 없이 CSS 로 됩니다.
   */
  const track = (event: React.MouseEvent<HTMLDivElement>) => {
    const box = event.currentTarget.getBoundingClientRect()
    setSpot({
      x: ((event.clientX - box.left) / box.width) * 100,
      y: ((event.clientY - box.top) / box.height) * 100,
    })
  }

  return (
    <div className="gallery">
      <div
        className="thumb"
        onMouseMove={current === undefined ? undefined : track}
        onMouseLeave={() => {
          setSpot(null)
        }}
      >
        {current === undefined ? (
          `상품 이미지 ${String(selected + 1)}`
        ) : (
          <img src={current} alt={`${alt} 이미지 ${String(selected + 1)}`} />
        )}

        {/*
          확대는 사진 위에 같은 사진을 덮어 처리합니다.
          아래 사진과 똑같이 object-fit:cover 로 깔고 transform-origin 을 커서에
          두고 키우면, 커서가 가리키는 지점이 제자리에 남은 채 주변만 커집니다.
          배경 이미지로 하면 cover 의 잘린 범위를 그대로 따라가지 못해 확대했을
          때 커서 아래와 다른 곳이 보입니다.

          aria-hidden 인 이유는 같은 사진을 한 번 더 읽어줄 이유가 없어서입니다.
        */}
        {current !== undefined && spot !== null && (
          <div className="zoom" aria-hidden="true">
            <img
              src={current}
              alt=""
              style={{
                transform: `scale(${String(ZOOM)})`,
                transformOrigin: `${String(spot.x)}% ${String(spot.y)}%`,
              }}
            />
          </div>
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
                  // 사진을 바꾸면 이전 사진의 확대 상태가 남지 않게 지웁니다.
                  setSpot(null)
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
