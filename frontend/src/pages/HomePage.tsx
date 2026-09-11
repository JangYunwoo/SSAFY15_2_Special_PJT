import { useQuery } from '@tanstack/react-query'

import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { GroupBuyGrid } from '@/features/groupBuy/GroupBuyGrid'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { ButtonLink } from '@/shared/ui/Button'
import { QueryBoundary } from '@/shared/ui/states'

const PREVIEW_SIZE = 8

export function HomePage() {
  useDocumentTitle()

  const query = useQuery(
    groupBuyQueries.list({ status: 'RECRUITING', sort: 'popular', size: PREVIEW_SIZE }),
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
          <ButtonLink to="/group-buys">공동구매 둘러보기</ButtonLink>
        </div>
      </section>

      <main className="wrap">
        <section className="section">
          <div className="sec-head">
            <div>
              <h2 className="sec-title">지금 참여할 수 있는 공동구매</h2>
              <p className="sec-sub">
                {query.data
                  ? `${String(query.data.meta.totalElements)}건의 공동구매가 진행 중입니다.`
                  : '진행 중인 공동구매를 불러오는 중입니다.'}
              </p>
            </div>
            <ButtonLink variant="ghost" to="/group-buys">
              상품 전체보기
            </ButtonLink>
          </div>

          <div className="mt24">
            <QueryBoundary query={query}>
              {(page) => <GroupBuyGrid items={page.items} />}
            </QueryBoundary>
          </div>
        </section>
      </main>
    </>
  )
}
