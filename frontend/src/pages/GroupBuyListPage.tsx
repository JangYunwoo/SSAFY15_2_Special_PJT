import { useQuery } from '@tanstack/react-query'

import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { CategoryTiles } from '@/features/category/CategoryTiles'
import { GroupBuyFilters } from '@/features/groupBuy/GroupBuyFilters'
import { GroupBuyGrid } from '@/features/groupBuy/GroupBuyGrid'
import { ALL_CATEGORIES, useGroupBuyListParams } from '@/features/groupBuy/useGroupBuyListParams'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Pagination } from '@/shared/ui/Pagination'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

/** 베스트 섹션에 세울 개수. 카드 그리드가 4열이라 딱 한 줄입니다 */
const BEST_SIZE = 4

export function GroupBuyListPage() {
  useDocumentTitle('상품 전체보기')

  const { keyword, categoryId, sort, queryParams, patch, goToPage } = useGroupBuyListParams()
  const query = useQuery(groupBuyQueries.list(queryParams))

  /*
   * 베스트는 화면의 조건(검색어·카테고리·정렬)과 무관하게 늘 같은 넷입니다.
   * 아래 목록을 잘라 쓰면 "농산물 + 최신순" 을 고른 순간 베스트도 그 안에서
   * 뽑혀, 말만 베스트지 그냥 첫 네 개가 됩니다. 그래서 따로 조회합니다.
   */
  const bestQuery = useQuery(
    groupBuyQueries.list({ status: 'RECRUITING', sort: 'popular', size: BEST_SIZE }),
  )
  const bestItems = bestQuery.data?.items.slice(0, BEST_SIZE) ?? []

  const isFiltered = keyword !== '' || categoryId !== ALL_CATEGORIES
  const total = query.data?.meta.totalElements

  const showBest = !isFiltered && bestItems.length > 0
  const bestIds = new Set(showBest ? bestItems.map((item) => item.id) : [])

  return (
    <>
      {/*
        배너는 홈에만 둡니다. 여기는 골라 보러 온 화면이라, 첫 화면을 배너가
        차지하면 카테고리와 베스트가 스크롤 아래로 밀립니다.
      */}
      <CategoryTiles activeId={categoryId} />

      <main className="wrap">
        {/*
          베스트는 조건을 걸지 않았을 때만 보여줍니다. 검색이나 카테고리로 좁혀
          들어온 사람은 그 결과를 보러 온 것이라, 그 위에 무관한 상품 넷이
          먼저 오면 방해가 됩니다.
        */}
        {showBest && (
          <section className="section">
            <div className="sec-head">
              <div>
                <h2 className="sec-title">베스트 상품</h2>
              </div>
            </div>
            <div className="mt24">
              <GroupBuyGrid items={bestItems} />
            </div>
          </section>
        )}

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
              {/*
                건수는 검색·카테고리로 좁혀 들어왔을 때만 적습니다. 그때는 "찾았는가"
                가 알고 싶은 것이고 베스트 섹션도 안 나와 숫자가 정확합니다.
                전체 목록에서는 위 베스트 넷을 빼고 그리므로 숫자와 보이는 카드 수가
                어긋납니다. 어차피 답할 질문도 아니라 적지 않습니다.
              */}
              {isFiltered && (
                <p className="sec-sub" aria-live="polite">
                  {total === undefined ? '찾는 중입니다.' : `${String(total)}건을 찾았습니다.`}
                </p>
              )}
            </div>
            {/* 정렬은 제목과 같은 줄 오른쪽 끝입니다 */}
            <GroupBuyFilters sort={sort} onChange={patch} />
          </div>

          <QueryBoundary query={query}>
            {(result) => {
              /*
                위 베스트 섹션에 이미 선 건은 뺍니다. 같은 카드가 한 화면에 두 번
                보이면 베스트가 따로 고른 것처럼 읽히지 않고, 아래 목록도 첫 줄이
                방금 본 것의 반복이 됩니다.
              */
              const rest = result.items.filter((item) => !bestIds.has(item.id))

              // 빈 상태는 서버가 아무것도 주지 않았을 때만입니다. 베스트로 다 빠져서
              // 비었다면 "결과가 없습니다" 가 아니라 정말로 그것이 전부입니다.
              if (result.items.length === 0) {
                return (
                  <EmptyState
                    title={
                      keyword
                        ? `'${keyword}' 검색 결과가 없습니다`
                        : '이 카테고리에는 아직 없습니다'
                    }
                    {...(isFiltered
                      ? { action: { to: '/group-buys', label: '전체 공동구매 보기' } }
                      : {})}
                  >
                    {keyword
                      ? '다른 검색어를 넣거나 카테고리를 넓혀 보세요.'
                      : '다른 카테고리에는 모집 중인 공동구매가 있을 수 있습니다.'}
                  </EmptyState>
                )
              }

              // 진행 중인 건이 베스트 넷 안에 다 들어가면 아래가 빕니다. 빈 자리를
              // 그대로 두면 안 불러온 것처럼 보여, 왜 없는지 한 줄로 적습니다.
              if (rest.length === 0) {
                return (
                  <p className="sec-sub mt24">
                    진행 중인 공동구매를 모두 위 베스트에 담았습니다.
                  </p>
                )
              }

              return (
                <>
                  <div className="mt24">
                    <GroupBuyGrid items={rest} />
                  </div>
                  <Pagination meta={result.meta} onChange={goToPage} label="공동구매 목록 페이지" />
                </>
              )
            }}
          </QueryBoundary>
        </section>
      </main>
    </>
  )
}





