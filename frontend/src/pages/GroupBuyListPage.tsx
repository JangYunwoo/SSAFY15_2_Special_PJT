import { useQuery } from '@tanstack/react-query'

import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { GroupBuyFilters } from '@/features/groupBuy/GroupBuyFilters'
import { GroupBuyGrid } from '@/features/groupBuy/GroupBuyGrid'
import { ALL_CATEGORIES, useGroupBuyListParams } from '@/features/groupBuy/useGroupBuyListParams'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

export function GroupBuyListPage() {
  useDocumentTitle('상품 전체보기')

  const { keyword, categoryId, sort, queryParams, patch } = useGroupBuyListParams()
  const query = useQuery(groupBuyQueries.list(queryParams))

  const isFiltered = keyword !== '' || categoryId !== ALL_CATEGORIES
  const total = query.data?.meta.totalElements

  return (
    <main className="wrap">
      <section className="section">
        <div className="sec-head">
          <div>
            <h2 className="sec-title">상품 전체보기</h2>
            <p className="sec-sub" aria-live="polite">
              {total === undefined
                ? '진행 중인 공동구매를 불러오는 중입니다.'
                : isFiltered
                  ? `${String(total)}건을 찾았습니다.`
                  : `${String(total)}건의 공동구매가 진행 중입니다.`}
            </p>
          </div>
        </div>

        <GroupBuyFilters keyword={keyword} categoryId={categoryId} sort={sort} onChange={patch} />

        <QueryBoundary query={query}>
          {(page) =>
            page.items.length === 0 ? (
              <EmptyState>
                {keyword
                  ? `'${keyword}' 검색 결과가 없습니다.`
                  : '해당 카테고리에 진행 중인 공동구매가 없습니다.'}
              </EmptyState>
            ) : (
              <GroupBuyGrid items={page.items} />
            )
          }
        </QueryBoundary>
      </section>
    </main>
  )
}
