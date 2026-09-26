import type { GroupBuySummary } from '@/entities/groupBuy/model'

import { GroupBuyCard } from './GroupBuyCard'

/** 카드 그리드 (.cards) */
export function GroupBuyGrid({
  items,
  onItemClick,
}: {
  items: readonly GroupBuySummary[]
  onItemClick?: ((item: GroupBuySummary, position: number) => void) | undefined
}) {
  return (
    <div className="cards">
      {items.map((item, index) => (
        <GroupBuyCard
          key={item.id}
          item={item}
          onClick={onItemClick === undefined ? undefined : () => {
            onItemClick(item, index)
          }}
        />
      ))}
    </div>
  )
}

