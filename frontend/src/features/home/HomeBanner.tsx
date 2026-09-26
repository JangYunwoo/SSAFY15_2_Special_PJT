import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'

import { ChevronIcon, PauseIcon, PlayIcon } from '@/shared/ui/icons'

interface Slide {
  /** 배경 색조. 배너끼리 구분되게 슬라이드마다 다르게 둡니다 */
  tone: 'brand' | 'plum' | 'sand' | 'moss'
  title: string
  highlight: string
  tail: string
  description: string
}

/**
 * 홈 배너에 돌릴 내용.
 *
 * 할인율이나 기간 한정 같은 판촉 문구는 넣지 않았습니다. 실제로 그런 행사가
 * 없는데 배너에 적으면 화면이 거짓말을 합니다. 대신 이 서비스가 실제로 하는 일을
 * 돌립니다. 행사 배너가 생기면 이 배열을 서버 응답으로 바꾸면 됩니다.
 */
const SLIDES: readonly Slide[] = [
  {
    tone: 'brand',
    title: '이거 어디서 샀어? 이거 ',
    highlight: '공구한',
    tail: ' 건데',
    description: '필요한 사람끼리 모여 더 싸게, 결제는 에스크로로 안전하게',
  },
  {
    tone: 'plum',
    title: '모일수록 ',
    highlight: '분담금',
    tail: '이 내려갑니다',
    description: '상품 가격을 목표 인원이 나눠 냅니다',
  },
  {
    tone: 'sand',
    title: '결제한 돈은 ',
    highlight: '에스크로',
    tail: '에 보관됩니다',
    description: '구매확정 전까지 판매자에게 넘어가지 않습니다',
  },
  {
    tone: 'moss',
    title: '모집이 미달하면 ',
    highlight: '자동 환불',
    tail: '됩니다',
    description: '인원이 안 차도 결제금액은 그대로 돌아옵니다',
  },
]

const TOTAL = SLIDES.length
const INTERVAL_MS = 5000
/** global.css 의 .banner-track transition 과 맞춰야 합니다 */
const SLIDE_MS = 400

/**
 * 양 끝에 복제본을 하나씩 붙인 목록.
 *
 * [마지막, 1, 2, 3, 4, 첫번째]
 *
 * 맨 앞과 맨 뒤의 복제본 덕분에 첫 장에서도 왼쪽에, 마지막 장에서도 오른쪽에
 * 걸쳐 보이는 배너가 있습니다. 복제본이 없으면 양 끝에서 한쪽이 빈 채로 섭니다.
 */
const DISPLAY: readonly Slide[] = [...SLIDES.slice(-1), ...SLIDES, ...SLIDES.slice(0, 1)]

/**
 * 홈 배너 캐러셀.
 *
 * 가운데 배너가 활성이고 좌우 배너가 화면 끝에서 걸쳐 보입니다. 한 장만 꽉 채우면
 * 뒤에 더 있다는 것이 드러나지 않아, 사용자가 넘겨볼 생각을 하지 않습니다.
 *
 * 자동 넘김은 항상 오른쪽으로만 갑니다. 마지막에서 첫 장으로 돌아올 때 왼쪽으로
 * 되감으면 흐름이 끊겨 "끝났다"로 읽힙니다. 그래서 끝의 복제본까지 평소처럼
 * 넘어간 다음, 애니메이션을 끈 채 같은 그림인 진짜 첫 장으로 자리만 바꿉니다.
 * 보는 쪽에서는 원통이 한 방향으로 계속 도는 것처럼 보입니다.
 *
 * 자동 재생에는 반드시 멈춤 버튼을 함께 둡니다. 읽는 도중에 바뀌면 다시 찾아야
 * 하는데 멈출 방법이 없으면 갇힙니다(WCAG 2.2.2). 마우스를 올리거나 안에서 탭
 * 이동을 하는 동안에도 멈추고, 움직임을 줄이도록 설정한 사용자에게는 멈춘 채 시작합니다.
 */
export function HomeBanner() {
  const trackRef = useRef<HTMLDivElement>(null)
  /** 한 칸 이동 거리(배너 폭 + 간격). 실측합니다 */
  const [step, setStep] = useState(0)

  /*
   * 이동 거리를 CSS calc 로 계산하지 않습니다. calc(-3 * (var(--banner-w) + 16px))
   * 처럼 min() 이 든 변수에 숫자를 곱하면 Chrome 이 곱수를 무시하고 한 칸만
   * 움직였습니다. 화면에서 잰 값이면 그런 해석 차이가 끼어들 자리가 없습니다.
   */
  useEffect(() => {
    const track = trackRef.current
    if (track === null) return
    const measure = () => {
      const first = track.querySelector('.banner-slide')
      if (first === null) return
      const gap = Number.parseFloat(getComputedStyle(track).columnGap) || 0
      setStep(first.getBoundingClientRect().width + gap)
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(track)
    return () => {
      observer.disconnect()
    }
  }, [])

  /** DISPLAY 기준 위치. 1 이 진짜 첫 장입니다 */
  const [pos, setPos] = useState(1)
  const [animate, setAnimate] = useState(true)
  const [paused, setPaused] = useState(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  )
  const hovering = useRef(false)

  const realIndex = (((pos - 1) % TOTAL) + TOTAL) % TOTAL

  useEffect(() => {
    if (paused) return
    const timer = setInterval(() => {
      if (!hovering.current) setPos((current) => current + 1)
    }, INTERVAL_MS)
    return () => {
      clearInterval(timer)
    }
  }, [paused])

  // 복제본에 도착하면 애니메이션을 끄고 같은 그림의 진짜 자리로 옮깁니다.
  useEffect(() => {
    if (pos !== 0 && pos !== TOTAL + 1) return
    const timer = setTimeout(() => {
      setAnimate(false)
      setPos(pos === 0 ? TOTAL : 1)
    }, SLIDE_MS)
    return () => {
      clearTimeout(timer)
    }
  }, [pos])

  // 자리를 옮긴 다음 애니메이션을 되살립니다. 같은 렌더에 되살리면 브라우저가 두
  // 변화를 한 번에 그려 되감기는 모습이 그대로 보입니다.
  //
  // requestAnimationFrame 을 쓰면 안 됩니다. 탭이 뒤로 가 있는 동안 rAF 는 아예
  // 멈춰서 콜백이 실행되지 않고, transition:none 이 붙은 채로 남습니다. 돌아오면
  // 배너가 애니메이션 없이 툭툭 끊겨 넘어갑니다. 타이머는 뒤에서도 (느려질지언정)
  // 실행되므로 그런 상태로 굳지 않습니다.
  useEffect(() => {
    if (animate) return
    const timer = setTimeout(() => {
      setAnimate(true)
    }, 50)
    return () => {
      clearTimeout(timer)
    }
  }, [animate])

  return (
    <section
      className="banner"
      aria-roledescription="carousel"
      aria-label="공구한 안내"
      onMouseEnter={() => {
        hovering.current = true
      }}
      onMouseLeave={() => {
        hovering.current = false
      }}
      onFocusCapture={() => {
        hovering.current = true
      }}
      onBlurCapture={() => {
        hovering.current = false
      }}
    >
      <div className="banner-viewport">
        <div
          ref={trackRef}
          className={`banner-track${animate ? '' : ' is-jumping'}`}
          style={{
            transform: `translateX(${String(-pos * step)}px)`,
            ...(animate ? {} : { transition: 'none' }),
          }}
        >
          {DISPLAY.map((slide, slideIndex) => {
            const isActive = slideIndex === pos
            return (
              <Link
                to="/group-buys"
                className={`banner-slide tone-${slide.tone}${isActive ? ' is-active' : ''}`}
                key={`${slide.highlight}-${String(slideIndex)}`}
                aria-label={`${slide.title}${slide.highlight}${slide.tail}`}
                aria-hidden={!isActive}
                tabIndex={isActive ? undefined : -1}
              >
                <h2>
                  {slide.title}
                  <b>{slide.highlight}</b>
                  {slide.tail}
                </h2>
                <p>{slide.description}</p>
              </Link>
            )
          })}
        </div>
      </div>

      <div className="banner-bottom">
        <div className="banner-progress" aria-hidden="true">
          <span style={{ width: `${String(((realIndex + 1) / TOTAL) * 100)}%` }} />
        </div>
        <div className="banner-controls">
          <button
            type="button"
            aria-label="이전 배너"
            onClick={() => {
              setPos((current) => current - 1)
            }}
          >
            <ChevronIcon direction="prev" size={16} />
          </button>
          <button
            type="button"
            aria-label="다음 배너"
            onClick={() => {
              setPos((current) => current + 1)
            }}
          >
            <ChevronIcon direction="next" size={16} />
          </button>
          <button
            type="button"
            aria-label={paused ? '배너 자동 넘김 시작' : '배너 자동 넘김 멈춤'}
            aria-pressed={paused}
            onClick={() => {
              setPaused((value) => !value)
            }}
          >
            {paused ? <PlayIcon /> : <PauseIcon />}
          </button>
        </div>
      </div>
    </section>
  )
}






