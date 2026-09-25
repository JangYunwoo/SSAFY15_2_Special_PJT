import { useQuery } from '@tanstack/react-query'

import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { GroupBuyGrid } from '@/features/groupBuy/GroupBuyGrid'
import { useLikedIds } from '@/features/like/likeStore'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

/**
 * 찜 목록.
 *
 * 로그인이 필요합니다(라우터의 RequireAuth). 찜은 계정에 딸린 것이라, 로그인
 * 전에는 보여줄 "내 찜" 자체가 없습니다.
 *
 * 찜 id 는 브라우저에만 있고 서버는 모릅니다. 그래서 id 로 조회하는 API 대신
 * 모집 중인 목록을 받아 걸러냅니다. 건수가 늘면 이 방식은 버티지 못하므로,
 * 서버에 찜이 생기면 그때 전용 조회로 바꿔야 합니다.
 *
 * 모집이 끝난 건은 목록 응답에서 빠지므로 여기서도 사라집니다. 찜은 "지금 살 수
 * 있는 것" 을 모아두는 자리라, 이미 닫힌 건이 남아 있는 편이 더 혼란스럽습니다.
 */
export default function LikesPage() {
  useDocumentTitle('찜한 공동구매')

  const likedIds = useLikedIds()
  const query = useQuery(groupBuyQueries.list({ status: 'RECRUITING', size: 100 }))

  return (
    <main className="wrap">
      <section className="section">
        <div className="sec-head">
          <div>
            <h2 className="sec-title">찜한 공동구매</h2>
            <p className="sec-sub">이 브라우저에만 저장됩니다. 다른 기기에서는 보이지 않습니다.</p>
          </div>
        </div>

        <QueryBoundary query={query}>
          {(page) => {
            const items = page.items.filter((item) => likedIds.includes(item.id))
            return items.length === 0 ? (
              <EmptyState title="찜한 공동구매가 없습니다" action={{ to: '/', label: '둘러보기' }}>
                마음에 드는 공동구매의 하트를 누르면 여기에 모입니다.
              </EmptyState>
            ) : (
              <GroupBuyGrid items={items} />
            )
          }}
        </QueryBoundary>
      </section>
    </main>
  )
}




