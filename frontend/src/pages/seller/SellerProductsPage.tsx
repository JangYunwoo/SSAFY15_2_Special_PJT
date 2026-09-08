import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'

import type { Product } from '@/entities/product/model'
import { productQueries } from '@/entities/product/queries'
import { groupBuyNo, number, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { StateChip, type StateTone } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

/** 연결된 공동구매 상태를 칩으로 표기합니다. */
function linkedChip(product: Product): { label: string; tone: StateTone } | null {
  if (product.linkedGroupBuyId === null) return null

  const no = groupBuyNo(product.linkedGroupBuyId)
  switch (product.linkedGroupBuyStatus) {
    case 'RECRUITING':
      return { label: `${no} 모집중`, tone: 'live' }
    case 'FULFILLED':
      return { label: `${no} 모집완료`, tone: 'ship' }
    case 'FAILED':
      return { label: `${no} 모집실패`, tone: 'alert' }
    default:
      return { label: no, tone: 'neutral' }
  }
}

export default function SellerProductsPage() {
  useDocumentTitle('상품 관리')

  const query = useQuery(productQueries.list({ size: 100 }))

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">상품 관리</h2>
          <p className="sec-sub">
            등록한 상품 {number(query.data?.meta.totalElements ?? 0)}건 · 상품을 먼저 등록한 뒤
            공동구매를 개설합니다.
          </p>
        </div>
        <ButtonLink to="/seller/products/new">상품 등록하기</ButtonLink>
      </div>

      <div className="panel">
        <QueryBoundary query={query}>
          {(page) =>
            page.items.length === 0 ? (
              <EmptyState>등록한 상품이 없습니다. 먼저 상품을 등록해 주세요.</EmptyState>
            ) : (
              <table className="list fixed">
                <colgroup>
                  <col style={{ width: '32%' }} />
                  <col style={{ width: '11%' }} />
                  <col style={{ width: '15%' }} />
                  <col style={{ width: '17%' }} />
                  <col style={{ width: '8%' }} />
                  <col style={{ width: '17%' }} />
                </colgroup>
                <thead>
                  <tr>
                    <th>상품</th>
                    <th>카테고리</th>
                    <th className="num">상품 가격</th>
                    <th>연결된 공동구매</th>
                    <th>수정</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((product) => {
                    const chip = linkedChip(product)
                    const editable = product.linkedGroupBuyId === null

                    return (
                      <tr key={product.id}>
                        <td>
                          <b>{product.name}</b>
                          <br />
                          <span className="small muted">
                            {[product.brand, product.color].filter(Boolean).join(' · ')}
                            {product.aiExtracted && ' · AI 등록'}
                          </span>
                        </td>
                        <td>{product.categoryName}</td>
                        <td className="num">{won(product.price)}</td>
                        <td>
                          {chip === null ? (
                            <span className="small muted">없음</span>
                          ) : (
                            <StateChip tone={chip.tone}>{chip.label}</StateChip>
                          )}
                        </td>
                        <td>{editable ? '가능' : <span className="small muted">불가</span>}</td>
                        <td className="num">
                          {editable ? (
                            <Link
                              className="btn-ghost"
                              to={`/seller/group-buys/new?productId=${product.id}`}
                            >
                              공동구매 열기
                            </Link>
                          ) : (
                            <span className="small muted">진행 중</span>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )
          }
        </QueryBoundary>

        <p className="small muted mt16" style={{ marginBottom: 0 }}>
          모집이 종료된 상품은 같은 상품으로 새 공동구매를 열 수 있으며, 이때 가격과 목표 인원을
          다시 정합니다.
        </p>
      </div>
    </section>
  )
}
