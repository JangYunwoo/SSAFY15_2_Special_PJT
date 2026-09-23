import { useQuery } from '@tanstack/react-query'

import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { CategoryTiles } from '@/features/category/CategoryTiles'
import { GroupBuyFilters } from '@/features/groupBuy/GroupBuyFilters'
import { GroupBuyGrid } from '@/features/groupBuy/GroupBuyGrid'
import { ALL_CATEGORIES, useGroupBuyListParams } from '@/features/groupBuy/useGroupBuyListParams'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Pagination } from '@/shared/ui/Pagination'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

export function GroupBuyListPage() {
  useDocumentTitle('상품 전체보기')

  const { keyword, categoryId, sort, queryParams, patch, goToPage } = useGroupBuyListParams()
  const query = useQuery(groupBuyQueries.list(queryParams))

  const isFiltered = keyword !== '' || categoryId !== ALL_CATEGORIES
  const total = query.data?.meta.totalElements

  return (
    <main className="wrap">
      <section className="section">
        <div className="sec-head">
          <div>
            {/*
              검색어는 제목으로 보여줍니다. 검색창을 헤더로 옮겼으므로, 무엇으로
              찾은 목록인지 화면 어딘가에는 적혀 있어야 합니다.
            */}
            <h2 className="sec-title">
              {keyword === '' ? '상품 전체보기' : `'${keyword}' 검색 결과`}
            </h2>
            <p className="sec-sub" aria-live="polite">
              {total === undefined
                ? '진행 중인 공동구매를 불러오는 중입니다.'
                : isFiltered
                  ? `${String(total)}건을 찾았습니다.`
                  : `${String(total)}건의 공동구매가 진행 중입니다.`}
            </p>
          </div>
        </div>

        <CategoryTiles activeId={categoryId} className="mt8" />

        <GroupBuyFilters sort={sort} onChange={patch} />

        <QueryBoundary query={query}>
          {(result) =>
            result.items.length === 0 ? (
              <EmptyState
                title={
                  keyword ? `'${keyword}' 검색 결과가 없습니다` : '이 카테고리에는 아직 없습니다'
                }
                {...(isFiltered
                  ? { action: { to: '/group-buys', label: '전체 공동구매 보기' } }
                  : {})}
              >
                {keyword
                  ? '다른 검색어를 넣거나 카테고리를 넓혀 보세요.'
                  : '다른 카테고리에는 모집 중인 공동구매가 있을 수 있습니다.'}
              </EmptyState>
            ) : (
              <>
                <GroupBuyGrid items={result.items} />
                <Pagination meta={result.meta} onChange={goToPage} label="공동구매 목록 페이지" />
              </>
            )
          }
        </QueryBoundary>
      </section>
    </main>
  )
}


