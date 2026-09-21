import type { GroupBuySummary } from '@/entities/groupBuy/model'

import { GroupBuyCard } from './GroupBuyCard'

/** 카드 그리드 (.cards) */
export function GroupBuyGrid({
  items,
  reasons,
  onItemClick,
}: {
  items: readonly GroupBuySummary[]
  reasons?: Readonly<Record<string, string>> | undefined
  onItemClick?: ((item: GroupBuySummary, position: number) => void) | undefined
}) {
  return (
    <div className="cards">
      {items.map((item, index) => (
        <GroupBuyCard
          key={item.id}
          item={item}
          reason={reasons?.[item.id]}
          onClick={onItemClick === undefined ? undefined : () => {
            onItemClick(item, index)
          }}
        />
      ))}
    </div>
  )
}
