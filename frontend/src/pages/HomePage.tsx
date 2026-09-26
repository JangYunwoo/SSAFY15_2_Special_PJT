import { useCallback, useMemo, type ReactNode } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'

import type { GroupBuySummary } from '@/entities/groupBuy/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { recommendationQueries } from '@/entities/recommendation/queries'
import { useActor, useIsSignedIn } from '@/features/auth/sessionStore'
import { CategoryTiles } from '@/features/category/CategoryTiles'
import { HomeBanner } from '@/features/home/HomeBanner'
import { GroupBuyCarousel } from '@/features/groupBuy/GroupBuyCarousel'
import { GroupBuyGrid } from '@/features/groupBuy/GroupBuyGrid'
import { recordRecommendationEvents } from '@/features/recommendation/tracking'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ChevronIcon } from '@/shared/ui/icons'
import { QueryBoundary } from '@/shared/ui/states'

const PREVIEW_SIZE = 8

/**
 * 섹션 하나. 오른쪽의 "전체보기"는 그 섹션과 같은 조건이 걸린 목록으로 보냅니다.
 *
 * 홈은 여덟 개까지만 보여줍니다. 더 보고 싶을 때 갈 곳이 없으면 홈이 막다른
 * 길이 되고, 조건 없는 목록으로 보내면 방금 보던 기준이 사라집니다.
 */
function HomeSection({
  title,
  moreTo,
  children,
}: {
  title: string
  moreTo: string
  children: ReactNode
}) {
  return (
    <section className="section">
      <div className="sec-head">
        <div>
          <h2 className="sec-title">{title}</h2>
        </div>
        <Link className="sec-more" to={moreTo}>
          전체보기
          <ChevronIcon direction="next" size={14} />
        </Link>
      </div>
      <div className="mt24">{children}</div>
    </section>
  )
}

/**
 * 홈.
 *
 * 목록(/group-buys)과 역할을 나눕니다. 홈은 골라서 보여주는 곳이고, 목록은
 * 정렬·필터·페이지로 전부 뒤지는 곳입니다. 홈에 섹션이 하나뿐일 때는 둘이
 * 같은 화면처럼 보여서 목록이 필요 없어 보였습니다.
 */
export function HomePage() {
  useDocumentTitle()
  const isSignedIn = useIsSignedIn()
  const actor = useActor()
  const userId = isSignedIn ? actor?.id ?? null : null

  // 마감 임박과 인기는 정렬 기준이 달라 목록을 따로 받습니다. 한 번 받아 화면에서
  // 다시 줄 세우면 "전체보기" 로 넘어간 목록과 순서가 달라집니다.
  const deadlineQuery = useQuery(
    groupBuyQueries.list({ status: 'RECRUITING', sort: 'deadline', size: PREVIEW_SIZE }),
  )
  const popularQuery = useQuery(
    groupBuyQueries.list({
      status: 'RECRUITING',
      sort: 'popular',
      size: isSignedIn ? PREVIEW_SIZE * 2 : PREVIEW_SIZE,
    }),
  )

  const recommendationQuery = useQuery({
    ...recommendationQueries.list(userId, PREVIEW_SIZE),
    enabled: userId !== null,
  })
  const recommended = useMemo(
    () => (userId === null ? [] : recommendationQuery.data?.items ?? []),
    [userId, recommendationQuery.data?.items],
  )
  const personalized = recommended.length > 0
  const recommendedItems = recommended.map((item) => item.groupBuy)
  const recommendedIds = new Set(recommendedItems.map((item) => item.id))
  // 추천에 이미 나온 건은 인기에서 뺍니다. 같은 카드가 위아래로 두 번 보이면
  // 추천이 따로 고른 것처럼 읽히지 않습니다.
  const popularItems =
    popularQuery.data?.items.filter((item) => !recommendedIds.has(item.id)).slice(0, PREVIEW_SIZE) ??
    []
  const reasons = Object.fromEntries(recommended.map((item) => [item.groupBuy.id, item.reason]))

  const recordImpression = useCallback((item: GroupBuySummary, position: number) => {
    void recordRecommendationEvents([
      { groupBuyId: item.id, eventType: 'IMPRESSION', source: 'HOME_RECOMMENDATION', position },
    ])
  }, [])

  const recordClick = useCallback(
    (source: 'HOME_RECOMMENDATION' | 'HOME_POPULAR') =>
      (item: GroupBuySummary, position: number) => {
        void recordRecommendationEvents([
          { groupBuyId: item.id, eventType: 'CLICK', source, position },
        ])
      },
    [],
  )

  const recommendationClick = useMemo(() => recordClick('HOME_RECOMMENDATION'), [recordClick])
  const popularClick = useMemo(() => recordClick('HOME_POPULAR'), [recordClick])

  return (
    <>
      <HomeBanner />

      <CategoryTiles />

      <main className="wrap">
        {personalized && (
          <section className="section home-recommendation-section">
            <div className="sec-head">
              <div>
                <h2 className="sec-title">이런 상품은 어떠세요?</h2>
              </div>
            </div>
            <div className="mt24">
              <GroupBuyCarousel
                key={userId}
                items={recommendedItems}
                label="회원님을 위한 공동구매"
                reasons={reasons}
                onItemClick={recommendationClick}
                onItemVisible={recordImpression}
              />
            </div>
          </section>
        )}

        <HomeSection
          title="곧 모집이 끝나요!"
          moreTo="/group-buys?sort=deadline"
        >
          <QueryBoundary query={deadlineQuery}>
            {(page) => <GroupBuyGrid items={page.items.slice(0, PREVIEW_SIZE)} />}
          </QueryBoundary>
        </HomeSection>

        <HomeSection
          title="많이 찾는 상품"
          moreTo="/group-buys?sort=popular"
        >
          <QueryBoundary query={popularQuery}>
            {(page) =>
              isSignedIn ? (
                <GroupBuyCarousel
                  items={personalized ? popularItems : page.items.slice(0, PREVIEW_SIZE)}
                  label="인기 공동구매"
                  onItemClick={popularClick}
                />
              ) : (
                <GroupBuyGrid items={page.items.slice(0, PREVIEW_SIZE)} />
              )
            }
          </QueryBoundary>
        </HomeSection>
      </main>
    </>
  )
}




