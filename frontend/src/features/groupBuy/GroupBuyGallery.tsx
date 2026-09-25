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

/*
 * 돋보기 창 한 변 (px).
 *
 * 크기를 여기서만 정합니다. 예전에는 이 값과 CSS 의 width/height 가 따로
 * 적혀 있었는데, 한쪽만 고치면 창은 커졌는데 확대된 사진은 엉뚱한 곳을
 * 가리킵니다. 아래 계산이 모두 이 값을 쓰기 때문입니다.
 */
const LENS = 104

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

/*
 * 꺾쇠는 글자(‹ ›) 대신 SVG 로 그립니다. 글자는 좌우 여백이 불균등해 원 안에서
 * 가운데가 미묘하게 어긋나고, 폰트마다 모양도 달라집니다.
 */
function Chevron({ direction }: { direction: 'prev' | 'next' }) {
  return (
    <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true">
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

export function GroupBuyGallery({ images, alt }: GroupBuyGalleryProps) {
  const [selected, setSelected] = useState(0)
  const [lens, setLens] = useState<Lens | null>(null)
  const count = images.length > 0 ? images.length : PLACEHOLDER_COUNT
  const current = images[selected]

  /* 끝에서 한 번 더 누르면 반대쪽 끝으로 돌아갑니다. 막다른 화살표는 눌러도
     아무 일이 없어, 고장인지 끝인지 구분되지 않습니다. */
  const move = (step: number) => {
    setSelected((index) => (index + step + count) % count)
    setLens(null)
  }

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
            style={{ left: lens.x - LENS / 2, top: lens.y - LENS / 2, width: LENS, height: LENS }}
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
        {count > 1 && (
          <>
            {/*
            좌우 화살표는 사진 위에 얹습니다. 마우스를 올리면 돋보기를 끕니다 —
            화살표를 누르려는데 그 아래에서 확대창이 따라다니면 어수선합니다.
          */}
            <button
              type="button"
              className="gallery-nav prev"
              aria-label="이전 이미지"
              onMouseEnter={() => {
                setLens(null)
              }}
              onClick={() => {
                move(-1)
              }}
            >
              <Chevron direction="prev" />
            </button>
            <button
              type="button"
              className="gallery-nav next"
              aria-label="다음 이미지"
              onMouseEnter={() => {
                setLens(null)
              }}
              onClick={() => {
                move(1)
              }}
            >
              <Chevron direction="next" />
            </button>
          </>
        )}
      </div>

      {count > 1 && (
        <div className="gallery-dots" role="tablist" aria-label="상품 이미지">
          {Array.from({ length: count }, (_, index) => (
            <button
              key={index}
              type="button"
              role="tab"
              className={cx(index === selected && 'on')}
              aria-label={`이미지 ${String(index + 1)} 보기`}
              aria-selected={index === selected}
              onClick={() => {
                setSelected(index)
                setLens(null)
              }}
            />
          ))}
        </div>
      )}
    </div>
  )
}
