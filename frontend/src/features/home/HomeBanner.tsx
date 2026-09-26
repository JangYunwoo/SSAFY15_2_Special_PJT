import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { cx } from '@/shared/lib/cx'
import { ChevronIcon, PauseIcon, PlayIcon } from '@/shared/ui/icons'

import deskImage from '@/assets/banners/desk.webp'
import mainImage from '@/assets/banners/main.webp'
import potatoImage from '@/assets/banners/potato.webp'
import tumblerImage from '@/assets/banners/tumbler.jpg'

interface Slide {
  /** 배경 색조. 사진이 없는 슬라이드가 씁니다 */
  tone?: 'brand' | 'plum' | 'sand' | 'moss'
  /** 배경 사진. 있으면 tone 대신 이것을 깝니다 */
  image?: string
  /** 글자색. 사진마다 배경 밝기가 달라 슬라이드가 직접 정합니다 */
  ink?: string
  /** 글자 쪽 배경을 눌러 줍니다. 밝은 사진 위 흰 글자에 필요합니다 */
  scrim?: boolean
  /** 누르면 갈 곳 */
  to: string
  /** 제목. 칸마다 한 줄로 나옵니다 */
  lines: readonly string[]
  /** lines 안에서 브랜드색으로 강조할 조각 */
  highlight?: string
  /** 안내문구. 칸마다 한 줄 */
  description?: readonly string[]
}

/**
 * 홈 배너에 돌릴 내용.
 *
 * 첫 장은 이 서비스가 무엇인지 말하고, 나머지는 실제로 지금 모집 중인 공동구매로
 * 보냅니다. 네 장 모두 서비스 설명이던 때는 첫 화면 280px 을 같은 말 네 번에
 * 쓰면서, 정작 살 수 있는 물건은 스크롤을 내려야 나왔습니다.
 *
 * 사진에는 글자를 굽지 않습니다. 배너 폭이 680px 에서 모바일 330px 까지 줄어서
 * 구운 글자는 읽히지 않고, 문구를 고칠 때마다 사진을 다시 뽑아야 합니다.
 *
 * 감자 배너의 8910원은 적어 둔 값입니다. 모집 인원이 바뀌면 분담금도 달라지는데
 * 배너만 옛 숫자를 말하게 되므로, 실제로 쓰려면 공동구매 조회에서 받아야 합니다.
 */

/**
 * 제목 한 줄에서 강조할 조각만 브랜드색으로 감쌉니다.
 *
 * 전에는 title·highlight·tail 세 조각으로 나눠 받았는데, 줄바꿈이 필요한 문구가
 * 생기면서 조각 수가 늘어났습니다. 한 줄을 통째로 받고 강조할 말만 찾습니다.
 */
function highlighted(line: string, highlight: string | undefined): ReactNode {
  if (highlight === undefined) return line
  const at = line.indexOf(highlight)
  if (at < 0) return line
  return (
    <>
      {line.slice(0, at)}
      <b>{highlight}</b>
      {line.slice(at + highlight.length)}
    </>
  )
}

const SLIDES: readonly Slide[] = [
  {
    image: mainImage,
    to: '/group-buys',
    lines: ['이거 어디서 샀어?', '이거 공구한 건데'],
    highlight: '공구한',
    description: ['필요한 사람끼리 모여 더 싸게,', '결제는 에스크로로 안전하게'],
  },
  {
    image: tumblerImage,
    /*
     * 흰 글자는 이 사진 위에서 대비가 1.61:1 입니다(글자 자리 평균 rgb(198,210,150)).
     * 큰 글자 기준 최소 3:1 에도 한참 못 미쳐 실제로 읽히지 않습니다.
     * 어두운 글자로 바꾸면 6.51:1 이고, 흰 글자를 살리려면 사진 위에 가림막이
     * 필요합니다.
     */
    ink: '#ffffff',
    scrim: true,
    to: '/group-buys/5',
    lines: ['새학기도', '알록달록하게'],
    description: ['락앤락 메트로 킹 텀블러 820ml'],
  },
  {
    image: deskImage,
    ink: '#5c4433',
    to: '/group-buys/111',
    lines: ['좋은 느낌을 주는 책상'],
    description: ['가을맞이 책상 기획전'],
  },
  {
    image: potatoImage,
    to: '/group-buys/272',
    lines: ['왔어요 왔어', '모락모락 햇감자가'],
    description: ['국내산 햇감자 지금사면 8910원'],
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
                to={slide.to}
                className={cx(
                  'banner-slide',
                  slide.image === undefined ? `tone-${slide.tone ?? 'brand'}` : 'has-image',
                  slide.scrim === true && 'has-scrim',
                  isActive && 'is-active',
                )}
                style={{
                  ...(slide.image === undefined
                    ? {}
                    : { backgroundImage: `url(${slide.image})` }),
                  ...(slide.ink === undefined ? {} : { color: slide.ink }),
                }}
                key={`${slide.lines.join('')}-${String(slideIndex)}`}
                aria-label={slide.lines.join(' ')}
                aria-hidden={!isActive}
                tabIndex={isActive ? undefined : -1}
              >
                {/* 사진이 오른쪽에 있어 글은 왼쪽 절반만 씁니다 */}
                <span className="banner-text">
                  <h2>
                    {slide.lines.map((line, lineIndex) => (
                      <span className="banner-line" key={line}>
                        {highlighted(line, slide.highlight)}
                        {/* 마지막 줄이 아니면 줄을 바꿉니다 */}
                        {lineIndex < slide.lines.length - 1 && <br />}
                      </span>
                    ))}
                  </h2>
                  {slide.description !== undefined && (
                    <p>
                      {slide.description.map((line, lineIndex) => (
                        <span key={line}>
                          {line}
                          {lineIndex < (slide.description?.length ?? 0) - 1 && <br />}
                        </span>
                      ))}
                    </p>
                  )}
                </span>
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













