import { useEffect, useMemo, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'

import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { recommendationQueries } from '@/entities/recommendation/queries'
import { useIsSignedIn } from '@/features/auth/sessionStore'
import { GroupBuyGrid } from '@/features/groupBuy/GroupBuyGrid'
import { recordRecommendationEvents } from '@/features/recommendation/tracking'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { QueryBoundary } from '@/shared/ui/states'

const PREVIEW_SIZE = 8

export function HomePage() {
  useDocumentTitle()
  const isSignedIn = useIsSignedIn()
  const impressionSignature = useRef('')

  const query = useQuery(
    groupBuyQueries.list({ status: 'RECRUITING', sort: 'popular', size: PREVIEW_SIZE }),
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
  const displayedItems = personalized ? recommended.map((item) => item.groupBuy) : query.data?.items
  const reasons = Object.fromEntries(
    recommended.map((item) => [item.groupBuy.id, item.reason]),
  )

  useEffect(() => {
    if (!isSignedIn || recommended.length === 0) return
    const signature = recommended.map((item) => item.groupBuy.id).join(',')
    if (impressionSignature.current === signature) return
    impressionSignature.current = signature
    void recordRecommendationEvents(
      recommended.map((item, position) => ({
        groupBuyId: item.groupBuy.id,
        eventType: 'IMPRESSION',
        source: 'HOME_RECOMMENDATION',
        position,
      })),
    )
  }, [isSignedIn, recommended])

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
          <ButtonLink to="/group-buys" className="btn-arrow btn-on-brand">공동구매 둘러보기</ButtonLink>
        </div>
      </section>

      <main className="wrap">
        <section className="section">
          <div className="sec-head">
            <div>
              <h2 className="sec-title">
                {personalized ? '회원님을 위한 공동구매' : '지금 참여할 수 있는 공동구매'}
              </h2>
              <p className="sec-sub">
                {personalized
                  ? '최근 행동과 구매 이력을 반영한 추천입니다.'
                  : query.data
                  ? `${String(query.data.meta.totalElements)}건의 공동구매가 진행 중입니다.`
                  : '진행 중인 공동구매를 불러오는 중입니다.'}
              </p>
            </div>
          </div>

          <div className="mt24">
            {displayedItems === undefined ? (
              <QueryBoundary query={query}>{(page) => <GroupBuyGrid items={page.items} />}</QueryBoundary>
            ) : (
              <GroupBuyGrid
                items={displayedItems}
                reasons={personalized ? reasons : undefined}
                onItemClick={
                  personalized
                    ? (item, position) => {
                        void recordRecommendationEvents([
                          {
                            groupBuyId: item.id,
                            eventType: 'CLICK',
                            source: 'HOME_RECOMMENDATION',
                            position,
                          },
                        ])
                      }
                    : undefined
                }
              />
            )}
          </div>
        </section>
      </main>
    </>
  )
}
