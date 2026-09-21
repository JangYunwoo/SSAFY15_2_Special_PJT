import { useCallback, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'

import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { recommendationQueries } from '@/entities/recommendation/queries'
import { useIsSignedIn } from '@/features/auth/sessionStore'
import { GroupBuyCarousel } from '@/features/groupBuy/GroupBuyCarousel'
import { GroupBuyGrid } from '@/features/groupBuy/GroupBuyGrid'
import { recordRecommendationEvents } from '@/features/recommendation/tracking'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { QueryBoundary } from '@/shared/ui/states'

const PREVIEW_SIZE = 8

export function HomePage() {
  useDocumentTitle()
  const isSignedIn = useIsSignedIn()

  const query = useQuery(
    groupBuyQueries.list({
      status: 'RECRUITING',
      sort: 'popular',
      size: isSignedIn ? PREVIEW_SIZE * 2 : PREVIEW_SIZE,
    }),
  )
  const recommendationQuery = useQuery({
    ...recommendationQueries.list(PREVIEW_SIZE),
    enabled: isSignedIn,
  })
  const recommended = useMemo(
    () => recommendationQuery.data?.items ?? [],
    [recommendationQuery.data?.items],
  )
  const personalized = recommended.length > 0
  const recommendedItems = recommended.map((item) => item.groupBuy)
  const recommendedIds = new Set(recommendedItems.map((item) => item.id))
  const popularItems =
    query.data?.items.filter((item) => !recommendedIds.has(item.id)).slice(0, PREVIEW_SIZE) ?? []
  const reasons = Object.fromEntries(recommended.map((item) => [item.groupBuy.id, item.reason]))

  const recordImpression = useCallback(
    (item: (typeof recommendedItems)[number], position: number) => {
      void recordRecommendationEvents([
        {
          groupBuyId: item.id,
          eventType: 'IMPRESSION',
          source: 'HOME_RECOMMENDATION',
          position,
        },
      ])
    },
    [],
  )

  const recordClick = useCallback(
    (source: 'HOME_RECOMMENDATION' | 'HOME_POPULAR') =>
      (item: (typeof recommendedItems)[number], position: number) => {
        void recordRecommendationEvents([
          {
            groupBuyId: item.id,
            eventType: 'CLICK',
            source,
            position,
          },
        ])
      },
    [],
  )

  const recommendationClick = useMemo(() => recordClick('HOME_RECOMMENDATION'), [recordClick])
  const popularClick = useMemo(() => recordClick('HOME_POPULAR'), [recordClick])

  const popularCarousel = (items: typeof popularItems) => (
    <GroupBuyCarousel
      items={items}
      label="달성률 높은 공동구매"
      onItemClick={isSignedIn ? popularClick : undefined}
    />
  )

  const popularSection = (
    <section className="section">
      <div className="sec-head">
        <div>
          <h2 className="sec-title">
            {personalized ? '달성률 높은 공동구매' : '지금 참여할 수 있는 공동구매'}
          </h2>
          <p className="sec-sub">
            {query.data
              ? `${String(query.data.meta.totalElements)}건의 공동구매가 진행 중입니다.`
              : '진행 중인 공동구매를 불러오는 중입니다.'}
          </p>
        </div>
      </div>

      <div className="mt24">
        <QueryBoundary query={query}>
          {(page) =>
            personalized ? (
              popularCarousel(popularItems)
            ) : isSignedIn ? (
              popularCarousel(page.items.slice(0, PREVIEW_SIZE))
            ) : (
              <GroupBuyGrid items={page.items.slice(0, PREVIEW_SIZE)} />
            )
          }
        </QueryBoundary>
      </div>
    </section>
  )

  const recommendationSection = personalized && (
    <section className="section home-recommendation-section">
      <div className="sec-head">
        <div>
          <h2 className="sec-title">회원님을 위한 공동구매</h2>
          <p className="sec-sub">최근 행동과 구매 이력을 반영한 추천입니다.</p>
        </div>
      </div>

      <div className="mt24">
        <GroupBuyCarousel
          items={recommendedItems}
          label="회원님을 위한 공동구매"
          reasons={reasons}
          onItemClick={recommendationClick}
          onItemVisible={recordImpression}
        />
      </div>
    </section>
  )

  return (
    <>
      <section className="hero">
        <div className="inner">
          <h1>
            이거 어디서 샀어?
            <br />
            이거 <b>공구한</b> 건데
          </h1>
          <p>필요한 사람끼리 모여 더 싸게, 결제는 에스크로로 안전하게</p>
          <ButtonLink to="/group-buys" className="btn-arrow btn-on-brand">
            공동구매 둘러보기
          </ButtonLink>
        </div>
      </section>

      <main className="wrap">
        {recommendationSection}
        {popularSection}
      </main>
    </>
  )
}
