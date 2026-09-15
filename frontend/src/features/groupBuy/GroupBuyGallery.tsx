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

/** 돋보기 창 한 변 (px). 사진 폭의 3분의 1쯤이라 주변 맥락이 남습니다. */
const LENS = 180

interface GroupBuyGalleryProps {
  images: readonly string[]
  alt: string
}

/** 돋보기 창의 중심과, 계산에 쓸 사진 틀 크기 (px) */
interface Lens {
  x: number
  y: number
  width: number
  height: number
}

/** 돋보기 창이 사진 밖으로 나가지 않도록 중심을 안쪽으로 당깁니다. */
function clamp(value: number, max: number): number {
  const half = LENS / 2
  if (max <= LENS) return max / 2
  return Math.min(Math.max(value, half), max - half)
}

export function GroupBuyGallery({ images, alt }: GroupBuyGalleryProps) {
  const [selected, setSelected] = useState(0)
  const [lens, setLens] = useState<Lens | null>(null)
  const count = images.length > 0 ? images.length : PLACEHOLDER_COUNT
  const current = images[selected]

  const track = (event: React.MouseEvent<HTMLDivElement>) => {
    const box = event.currentTarget.getBoundingClientRect()
    setLens({
      x: clamp(event.clientX - box.left, box.width),
      y: clamp(event.clientY - box.top, box.height),
      width: box.width,
      height: box.height,
    })
  }

  return (
    <div className="gallery">
      <div
        className="thumb"
        onMouseMove={current === undefined ? undefined : track}
        onMouseLeave={() => {
          setLens(null)
        }}
      >
        {current === undefined ? (
          `상품 이미지 ${String(selected + 1)}`
        ) : (
          <img src={current} alt={`${alt} 이미지 ${String(selected + 1)}`} />
        )}

        {/*
          돋보기 창. 커서를 따라다니는 정사각형 안에서만 확대해 보여줍니다.
          사진 전체를 키우면 화면이 통째로 흔들려 어수선합니다.

          창 안에는 같은 사진을 배율만큼 키워 깔고, 커서가 가리키는 지점이 창
          한가운데 오도록 밀어 둡니다. 아래 사진과 같은 object-fit:cover 로
          깔아야 잘린 범위가 같아, 확대했을 때 커서 아래와 같은 곳이 보입니다.

          aria-hidden 인 이유는 같은 사진을 한 번 더 읽어줄 이유가 없어서입니다.
        */}
        {current !== undefined && lens !== null && (
          <div
            className="lens"
            aria-hidden="true"
            style={{ left: lens.x - LENS / 2, top: lens.y - LENS / 2 }}
          >
            <img
              src={current}
              alt=""
              style={{
                width: lens.width * ZOOM,
                height: lens.height * ZOOM,
                left: -(lens.x * ZOOM - LENS / 2),
                top: -(lens.y * ZOOM - LENS / 2),
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
                  // 사진을 바꾸면 이전 사진의 돋보기 상태가 남지 않게 지웁니다.
                  setLens(null)
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
