import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'

import type { Product } from '@/entities/product/model'
import { productQueries } from '@/entities/product/queries'
import { Steps } from '@/shared/ui/Steps'

import SellerGroupBuyNewPage from './SellerGroupBuyNewPage'

const product: Product = {
  id: '101', name: '검증용 상품', brand: null, modelName: null, color: null,
  categoryId: '1', categoryName: '디지털기기', price: 100000, description: '테스트',
  images: [], aiExtracted: false, withdrawalRestrictionReasonId: null,
  withdrawalRestrictionReason: null, restrictionReviewStatus: 'NONE',
  restrictionEvidenceUrl: null, linkedGroupBuyId: null, linkedGroupBuyStatus: null,
  createdAt: '2026-09-21T00:00:00Z',
}

function renderPage(items: Product[], url = '/seller/group-buys/new') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(productQueries.list({ size: 100 }).queryKey, {
    items, meta: { page: 0, size: 100, totalElements: items.length, totalPages: 1 },
  })
  try {
    return renderToStaticMarkup(
      <QueryClientProvider client={client}><MemoryRouter initialEntries={[url]}>
        <SellerGroupBuyNewPage />
      </MemoryRouter></QueryClientProvider>,
    )
  } finally { client.clear() }
}

describe('공동구매 생성의 상품 등록 진입', () => {
  it('상품이 없으면 바로 상품을 등록하는 링크를 제공한다', () => {
    const html = renderPage([])
    expect(html).toContain('선택할 수 있는 상품이 없습니다')
    expect(html).toMatch(/href="\/seller\/products\/new"[^>]*>상품 등록하기<\/a>/)
  })

  it('상품이 있어도 등록 단계와 새 상품 등록 링크로 이동할 수 있다', () => {
    const html = renderPage([product])
    expect(html).toMatch(/href="\/seller\/products\/new"[^>]*>1\. 상품 등록<\/a>/)
    expect(html).toMatch(/href="\/seller\/products\/new"[^>]*>새 상품 등록<\/a>/)
    expect(html).toContain('검증용 상품')
    expect(html).toContain('공동구매 생성하기')
  })

  it('등록 직후 productId로 돌아오면 해당 상품 선택을 유지한다', () => {
    const html = renderPage([product, { ...product, id: '102', name: '새 등록 상품' }], '/seller/group-buys/new?productId=102')
    expect(html).toMatch(/<option value="102" selected="">새 등록 상품<\/option>/)
  })

  it('이미 공구에 연결된 상품만 있어도 새 등록으로 진행할 수 있다', () => {
    const html = renderPage([{ ...product, linkedGroupBuyId: '20' }])
    expect(html).toContain('선택할 수 있는 상품이 없습니다')
    expect(html).toContain('상품 등록하기')
  })

  it('링크를 전달하지 않은 결제 단계 표시는 이동 링크로 바뀌지 않는다', () => {
    const html = renderToStaticMarkup(<Steps steps={['배송지', '결제']} current={1} />)
    expect(html).not.toContain('<a')
    expect(html).toContain('aria-current="step"')
  })
})
