import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'

import { PARTICIPATION_STATUS_DISPLAY } from '@/entities/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import type { ParticipationSummary } from '@/entities/participation/model'
import { displayFor } from '@/entities/statusDisplay'
import { date, dday, groupBuyNo, won } from '@/shared/lib/format'
import { StateChip } from '@/shared/ui/StateChip'

/**
 * 주문 목록의 한 건.
 *
 * 전에는 아코디언이라 누르면 그 자리에서 펼쳐졌습니다. 건이 늘면 목록이 길어져
 * 내가 어디를 보고 있었는지 놓치고, 펼친 내용에 있는 링크(구매확정·반품)로 나갔다
 * 돌아오면 다시 접혀 있었습니다. 상세를 별도 주소로 두면 돌아오기·새로고침·공유가
 * 모두 제자리를 찾습니다.
 *
 * 줄이 아니라 카드로 세웁니다. 한 줄에 상태·이름·금액을 옆으로 늘어놓으면 건마다
 * 어디를 봐야 할지가 매번 달라지는데, 카드로 끊고 위에서 아래로 상태 → 상품 →
 * 금액 순서를 고정하면 여러 건을 훑을 때 눈이 같은 자리만 따라가면 됩니다.
 */
export function OrderRow({ item }: { item: ParticipationSummary }) {
  const display = displayFor(PARTICIPATION_STATUS_DISPLAY, item.status, '참여 상태')
  // 자동 확정까지 며칠 남았는지는 상태 자체가 아니라 그 상태에 붙는 단서입니다.
  const remaining =
    item.status === 'DELIVERED' && item.autoConfirmAt !== null ? dday(item.autoConfirmAt) : null

  /*
   * 대표 이미지는 참여 응답(PAR-01)에 없어 공동구매에서 가져옵니다. 건마다 한 번씩
   * 부르지만 상세 화면과 같은 쿼리 키라 캐시를 나눠 쓰고, 목록에서 눌러 들어가면
   * 상세가 이미 받아 둔 값을 씁니다. 못 받아도 회색 자리만 남습니다.
   *
   * 참여 응답에 thumbnailUrl 이 생기면 이 조회는 지웁니다.
   */
  const groupBuy = useQuery(groupBuyQueries.detail(item.groupBuyId))
  const thumbnailUrl = groupBuy.data?.thumbnailUrl ?? null

  return (
    <Link className="order-card" to={`/orders/${item.id}`}>
      <span className="order-card-thumb">
        {thumbnailUrl !== null && <img src={thumbnailUrl} alt="" loading="lazy" />}
      </span>
      <span className="order-card-body">
        <span className="order-card-state">
          <StateChip tone={display.tone}>{display.label}</StateChip>
          {remaining !== null && <em>{remaining}</em>}
        </span>
        <span className="order-card-name">
          <span>{item.productName}</span>
        </span>
        <span className="order-card-pay">
          <b>{won(item.totalAmount)}</b>
          <span>{date(item.paidAt)} 결제</span>
        </span>
        <span className="order-card-meta">
          공동구매 {groupBuyNo(item.groupBuyId)} · {item.sellerName}
        </span>
      </span>
    </Link>
  )
}


