import type { ReactNode } from 'react'

import type { GroupBuyDetail } from '@/entities/groupBuy/model'
import { groupBuyNo, number, won } from '@/shared/lib/format'
import { DataRow, TotalRow } from '@/shared/ui/DataRow'

const NOTICES = [
  '목표 인원 미달 시 결제는 전액 자동 취소·환불됩니다.',
  '결제 금액은 구매확정 전까지 에스크로에 보관됩니다.',
  '모집 종료 전까지 참여를 취소할 수 있습니다.',
  '선점 시간이 지나면 자리가 해제되고 결제가 취소됩니다.',
] as const

interface OrderSummaryProps {
  groupBuy: GroupBuyDetail
  /** 결제 버튼 등 하단 영역 */
  children?: ReactNode | undefined
}

/** 결제 플로우 오른쪽 고정 요약 패널 */
export function OrderSummary({ groupBuy, children }: OrderSummaryProps) {
  const total = groupBuy.unitShareAmount + groupBuy.shippingFee

  return (
    <div className="panel">
      <h3>결제 금액</h3>
      <p className="sub">
        {groupBuy.productName} · 공동구매 {groupBuyNo(groupBuy.id)}
      </p>

      <DataRow
        label={
          <>
            상품 분담금{' '}
            <span className="small muted">
              {number(groupBuy.productPrice)}원 ÷ {groupBuy.targetCount}명
            </span>
          </>
        }
      >
        {won(groupBuy.unitShareAmount)}
      </DataRow>
      <DataRow label="배송비">{won(groupBuy.shippingFee)}</DataRow>
      <TotalRow label="총 결제 금액">{won(total)}</TotalRow>

      <div className="notice">
        <div className="notice-head">참여 전 확인해 주세요</div>
        <ul>
          {NOTICES.map((notice) => (
            <li key={notice}>{notice}</li>
          ))}
        </ul>
      </div>

      {children}
    </div>
  )
}
