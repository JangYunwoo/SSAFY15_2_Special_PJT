import { Link } from 'react-router'

import type { GroupBuySummary } from '@/entities/groupBuy/model'
import { won } from '@/shared/lib/format'

/**
 * 목록의 카드 한 장 (.card).
 *
 * 할인율과 정상가는 걷어냈습니다. 백엔드가 listPrice 를 products.price 로
 * 채우는데 그 값은 "공동구매 1회분 총액"이라, 1인 분담금을 목표 인원 전체의
 * 금액과 비교하게 됩니다. 목표 인원이 늘수록 할인율이 올라가는 수치였습니다.
 */
export function GroupBuyCard({ item }: { item: GroupBuySummary }) {
  return (
    <Link className="card" to={`/group-buys/${item.id}`}>
      <div className="thumb">
        {item.thumbnailUrl !== null && <img src={item.thumbnailUrl} alt="" loading="lazy" />}
      </div>
      <div className="body">
        <h3>{item.productName}</h3>
        <div className="cost">
          <b>{won(item.unitShareAmount)}</b>
        </div>
        <div className="meta">
          {item.paidCount} / {item.targetCount}명
        </div>
      </div>
    </Link>
  )
}
