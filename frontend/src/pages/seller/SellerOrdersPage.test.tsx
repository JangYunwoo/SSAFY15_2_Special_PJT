import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import type { SellerGroupBuyDetail } from '@/entities/groupBuy/model'
import SellerOrdersPage from './SellerOrdersPage'

function page(search: string, detail = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const fixture: SellerGroupBuyDetail = {
    id: '281', productId: '1', productName: '첫 공구', status: 'RECRUITING',
    targetCount: 5, paidCount: 0, confirmedCount: 0, productPrice: 5000,
    unitShareAmount: 1000, shippingFee: 0, recruitEndAt: '2026-10-01T00:00:00Z',
    invoiceRegisteredRate: null, recruitStartAt: '2026-09-01T00:00:00Z', roundingSurplus: 0,
    platformFeeRate: 0, escrowHeldAmount: 0, estimatedSettlementAmount: 0,
    returnCount: 0, canEdit: true, canCancel: true,
  }
  client.setQueryData(groupBuyQueries.sellerList({ size: 100 }).queryKey, {
    items: [fixture], meta: { page: 0, size: 100, totalElements: 1, totalPages: 1 },
  })
  if (detail) client.setQueryData(groupBuyQueries.sellerDetail('101').queryKey, { ...fixture, id: '101', productName: '선택한 공구' })
  try {
    return renderToStaticMarkup(<QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/seller/orders${search}`]}><SellerOrdersPage /></MemoryRouter>
    </QueryClientProvider>)
  } finally { client.clear() }
}
describe('판매자 주문 화면 선택 연결', () => {
  it('첫 페이지 밖 공구는 상세 확인 전에도 다른 공구로 선택하지 않는다', () => {
    expect(page('?groupBuyId=101')).toContain('value="101" selected=""')
    expect(page('?groupBuyId=101')).not.toContain('value="281" selected=""')
  })
  it('소유 상세 확인 후 해당 상품명을 선택 항목에 표시한다', () => {
    expect(page('?groupBuyId=101', true)).toContain('value="101" selected="">선택한 공구')
  })
  it('잘못된 주소는 오류를 보여주고 다른 주문을 열지 않는다', () => {
    expect(page('?groupBuyId=bad')).toContain('공동구매 번호가 올바르지 않습니다')
    expect(page('?groupBuyId=bad')).not.toContain('<h3>참여자 주문</h3>')
    expect(page('?groupBuyId=bad')).toContain('value="" disabled="" selected=""')
  })
})
