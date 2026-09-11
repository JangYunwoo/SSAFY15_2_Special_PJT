import type { GroupBuySummary } from '@/entities/groupBuy/model'

import { GroupBuyCard } from './GroupBuyCard'

/** 카드 그리드 (.cards) */
export function GroupBuyGrid({ items }: { items: readonly GroupBuySummary[] }) {
  return (
    <div className="cards">
      {items.map((item) => (
        <GroupBuyCard key={item.id} item={item} />
      ))}
    </div>
  )
}
