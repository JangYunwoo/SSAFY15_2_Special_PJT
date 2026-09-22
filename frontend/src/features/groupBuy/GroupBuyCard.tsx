import { Link } from 'react-router'

import type { GroupBuySummary } from '@/entities/groupBuy/model'
import { progressRate } from '@/shared/config/policy'
import { dday, won } from '@/shared/lib/format'
import { ProgressBar } from '@/shared/ui/ProgressBar'
import { StateChip } from '@/shared/ui/StateChip'

import { cardStatusDisplay } from './cardStatus'

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

  return (
    <Link className="card" to={`/group-buys/${item.id}`} onClick={onClick}>
      <div className="thumb">
        {item.thumbnailUrl !== null && <img src={item.thumbnailUrl} alt="" loading="lazy" />}
        <span className="card-badge">
          <StateChip tone={status.tone}>{status.label}</StateChip>
        </span>
      </div>
      <div className="body">
        <h3>{item.productName}</h3>
        <div className="cost">
          <b>{won(item.unitShareAmount)}</b>
        </div>
        <ProgressBar current={item.paidCount} target={item.targetCount} />
        <div className="meta card-progress">
          <span>
            {item.paidCount} / {item.targetCount}명 · {rate}%
          </span>
          <span>{dday(item.recruitEndAt)}</span>
        </div>
        {reason !== undefined && <div className="meta">{reason}</div>}
      </div>
    </Link>
  )
}
