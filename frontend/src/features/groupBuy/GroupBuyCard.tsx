import { Link, useLocation, useNavigate } from 'react-router'

import type { GroupBuySummary } from '@/entities/groupBuy/model'
import { useIsLiked, useToggleLike } from '@/features/like/likeStore'
import { progressRate } from '@/shared/config/policy'
import { cx } from '@/shared/lib/cx'
import { countdown, dday, number } from '@/shared/lib/format'
import { useCountdown } from '@/shared/lib/hooks/useCountdown'
import { ProgressBar } from '@/shared/ui/ProgressBar'
import { HeartIcon } from '@/shared/ui/icons'
import { StateChip } from '@/shared/ui/StateChip'

import { cardStatusDisplay, isImminent } from './cardStatus'

/**
 * 마감이 코앞인 건에만 붙는 카운트다운.
 *
 * 남은 기간이 긴 건까지 초를 세면 숫자가 계속 움직이는 목록이 되어, 정작 급한
 * 건이 묻힙니다. 뱃지가 "마감 임박" 인 건과 같은 기준으로만 켭니다.
 *
 * 시계는 useNow 의 공용 타이머 하나를 여럿이 구독합니다. 카드마다 setInterval 을
 * 들고 있으면 한 화면에서 타이머가 여덟 개 돕니다.
 */
function DeadlineTimer({ recruitEndAt }: { recruitEndAt: string }) {
  const { secondsLeft, isExpired } = useCountdown(recruitEndAt)
  if (isExpired) return null

  return (
    <span className="card-timer">
      {countdown(secondsLeft)} <i>남음</i>
    </span>
  )
}

/**
 * 카드 위의 찜 버튼.
 *
 * 카드 전체가 <a> 라 그 안의 버튼을 누르면 상세로도 함께 넘어갑니다.
 * preventDefault 로 이동을 막고 찜만 처리합니다.
 *
 * 로그인하지 않았으면 로그인 화면으로 보냅니다. 버튼을 감추지 않는 이유는, 이런
 * 기능이 있다는 것 자체가 로그인할 이유가 되기 때문입니다. 상세의 "공동구매
 * 참여하기" 도 같은 방식으로 동작합니다.
 */
function LikeButton({ id, name }: { id: string; name: string }) {
  const navigate = useNavigate()
  const { pathname, search } = useLocation()
  const liked = useIsLiked(id)
  const toggleLike = useToggleLike()

  return (
    <button
      type="button"
      className={cx('card-like', liked && 'on')}
      aria-pressed={liked}
      aria-label={`${name} ${liked ? '찜 해제' : '찜하기'}`}
      onClick={(event) => {
        event.preventDefault()
        event.stopPropagation()
        if (!toggleLike(id)) {
          void navigate('/login', { state: { from: `${pathname}${search}` } })
        }
      }}
    >
      <HeartIcon filled={liked} />
    </button>
  )
}

/**
 * 목록의 카드 한 장 (.card).
 *
 * 할인율과 정상가는 걷어냈습니다. 백엔드가 listPrice 를 products.price 로
 * 채우는데 그 값은 "공동구매 1회분 총액"이라, 1인 분담금을 목표 인원 전체의
 * 금액과 비교하게 됩니다. 목표 인원이 늘수록 할인율이 올라가는 수치였습니다.
 *
 * 그래서 카드가 답해야 하는 질문을 남은 세 가지로 잡았습니다 (S15P21B205-226):
 * 얼마인가(분담금), 얼마나 찼나(게이지), 언제 닫히나(D-day·뱃지). 참여 인원을
 * "3 / 20명" 숫자로만 두면 그게 많은 건지 적은 건지 읽는 쪽이 계산해야 해서
 * 게이지와 퍼센트를 같이 답니다.
 *
 * 금액은 숫자와 '원' 의 무게를 나눕니다. 통째로 굵게 두면 글자가 한 덩어리로
 * 보여 값이 눈에 먼저 들어오지 않습니다.
 */
export function GroupBuyCard({
  item,
  reason,
  onClick,
}: {
  item: GroupBuySummary
  reason?: string | undefined
  onClick?: (() => void) | undefined
}) {
  const status = cardStatusDisplay(item)
  const rate = progressRate(item.paidCount, item.targetCount)
  const imminent = isImminent(item)

  return (
    <Link className="card" to={`/group-buys/${item.id}`} onClick={onClick}>
      <div className="thumb">
        {item.thumbnailUrl !== null && <img src={item.thumbnailUrl} alt="" loading="lazy" />}
        <span className="card-tags">
          <StateChip tone={status.tone}>{status.label}</StateChip>
          {imminent && <DeadlineTimer recruitEndAt={item.recruitEndAt} />}
        </span>
        <LikeButton id={item.id} name={item.productName} />
      </div>
      <div className="body">
        <h3>{item.productName}</h3>
        <div className="cost">
          <b>{number(item.unitShareAmount)}</b>
          <span className="cost-unit">원</span>
        </div>
        <ProgressBar current={item.paidCount} target={item.targetCount} />
        <div className="meta card-progress">
          <span>
            {item.paidCount} / {item.targetCount}명 · {rate}%
          </span>
          {/* 카운트다운이 떠 있으면 D-day 는 같은 말을 두 번 하는 셈입니다 */}
          {!imminent && <span>{dday(item.recruitEndAt)}</span>}
        </div>
        {reason !== undefined && <div className="meta">{reason}</div>}
      </div>
    </Link>
  )
}





