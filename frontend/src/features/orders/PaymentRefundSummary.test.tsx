import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { PaymentHistoryItem } from '@/entities/payment/model'
import { PaymentRefundSummary } from './PaymentRefundSummary'

const payment: PaymentHistoryItem = {
  paymentId: '1', groupBuyId: '2', status: 'PAID', method: 'CARD', participationId: '3',
  amount: { shareAmount: 1000, shippingFee: 0, totalAmount: 1000, currency: 'KRW' },
  remainingSeconds: 0, slotExpiresAt: '2026-09-21T00:00:00Z',
}
const render = (refund?: PaymentHistoryItem['refund']) => renderToStaticMarkup(
  <PaymentRefundSummary payment={{ ...payment, ...(refund === undefined ? {} : { refund }) }} />,
)
describe('원승인과 별도로 보여주는 환불 원장 요약', () => {
  it('전액 환불 완료와 실제 완료 금액을 보여준다', () => {
    const html = render({ status: 'COMPLETED', completedAmount: 1000 })
    expect(html).toContain('환불 완료')
    expect(html).toContain('반환 완료 1,000원')
    expect(html).not.toContain('부분 환불')
  })
  it('부분 환불은 원승인 전액 반환으로 표시하지 않는다', () => {
    expect(render({ status: 'COMPLETED', completedAmount: 700 })).toContain('부분 환불 완료')
  })
  it('공제 후 0원으로 완료된 원장도 처리 완료로 표시한다', () => {
    const html = render({ status: 'COMPLETED', completedAmount: 0 })
    expect(html).toContain('환불 처리 완료(반환액 0원)')
    expect(html).not.toContain('반환 완료')
    expect(html).not.toContain('부분 환불')
  })
  it.each([['PROCESSING', '환불 처리 중'], ['FAILED', '환불 실패'], ['UNKNOWN', '환불 결과 확인 필요']] as const)('%s는 완료로 표시하지 않는다', (status, label) => {
    const html = render({ status, completedAmount: 0 })
    expect(html).toContain(label)
    expect(html).not.toContain('반환 완료')
  })
  it('환불 원장이 없으면 추가 상태가 없다', () => {
    expect(render({ status: 'NONE', completedAmount: 0 })).toBe('')
  })
  it('구 배포의 응답 누락은 환불 없음으로 단정하지 않는다', () => {
    expect(render()).toContain('환불 정보 미제공')
  })
})
