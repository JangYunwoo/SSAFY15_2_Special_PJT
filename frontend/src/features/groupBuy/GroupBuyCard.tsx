import { Link } from 'react-router'

import type { GroupBuySummary } from '@/entities/groupBuy/model'
import { discountRate } from '@/shared/config/policy'
import { won } from '@/shared/lib/format'

import { statusTag } from './statusTag'

/** 목록의 카드 한 장 (.card) */
export function GroupBuyCard({ item }: { item: GroupBuySummary }) {
  const discount = discountRate(item.listPrice, item.unitShareAmount)

  return (
    <Link className="card" to={`/group-buys/${item.id}`}>
      <div className="thumb">
        <span className="tag">{statusTag(item)}</span>
        {item.thumbnailUrl !== null && <img src={item.thumbnailUrl} alt="" loading="lazy" />}
      </div>
      <div className="body">
        <h3>{item.productName}</h3>
        <div className="cost">
          {discount > 0 && <span className="off">{discount}%</span>}
          <b>{won(item.unitShareAmount)}</b>
        </div>
        <div className="meta">
          <s>{won(item.listPrice)}</s>
          <i>·</i>
          {item.paidCount} / {item.targetCount}명
        </div>
      </div>
    </Link>
  )
}
