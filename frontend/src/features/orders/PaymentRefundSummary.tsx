import type { PaymentHistoryItem } from '@/entities/payment/model'
import { won } from '@/shared/lib/format'

export function PaymentRefundSummary({ payment }: { payment: PaymentHistoryItem }) {
  const refund = payment.refund
  if (refund === undefined) return <span className="rowsub">환불 정보 미제공</span>
  if (refund.status === 'NONE') return null
  const label = refund.status === 'UNKNOWN' ? '환불 결과 확인 필요'
    : refund.status === 'PROCESSING' ? '환불 처리 중'
      : refund.status === 'FAILED' ? '환불 실패'
        : refund.completedAmount === 0 ? '환불 처리 완료(반환액 0원)'
          : refund.completedAmount < payment.amount.totalAmount ? '부분 환불 완료' : '환불 완료'
  return <span className="rowsub">{label}
    {refund.completedAmount > 0 && <><br />반환 완료 {won(refund.completedAmount)}</>}
  </span>
}
