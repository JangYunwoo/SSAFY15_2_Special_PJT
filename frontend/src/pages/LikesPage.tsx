import { useQueries } from '@tanstack/react-query'

import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { GroupBuyGrid } from '@/features/groupBuy/GroupBuyGrid'
import { useLikedIds } from '@/features/like/likeStore'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/states'

/**
 * 찜 목록.
 *
 * 로그인이 필요합니다(라우터의 RequireAuth). 찜은 계정에 딸린 것이라, 로그인
 * 전에는 보여줄 "내 찜" 자체가 없습니다.
 *
 * 찜 id 는 브라우저에만 있고 서버는 모릅니다. 그래서 id 로 한 건씩 조회합니다.
 * 전에는 모집 중 목록을 100개 받아 걸러냈는데, 배포 서버에 공동구매가 210개라
 * 뒤쪽 100여 개를 찜하면 화면에 나오지 않았습니다. 목록 크기를 키우는 것은
 * 상품이 늘면 또 넘칩니다.
 *
 * 찜한 수만큼 요청이 나가지만 보통 몇 건이고, 상세 화면과 같은 쿼리 키라 캐시를
 * 나눠 씁니다. 서버에 찜 API 가 생기면 전용 조회 한 번으로 바뀝니다.
 *
 * 모집이 끝난 건도 그대로 보여줍니다. 카드에 "마감" 이 찍히므로 상태는 읽히고,
 * 찜해 둔 것이 말없이 사라지는 편이 더 혼란스럽습니다.
 */
export default function LikesPage() {
  useDocumentTitle('찜한 공동구매')

  const likedIds = useLikedIds()
  const results = useQueries({
    queries: likedIds.map((id) => groupBuyQueries.detail(id)),
  })

  const items = results.flatMap((result) => (result.data === undefined ? [] : [result.data]))
  const loading = results.some((result) => result.isPending)
  // 지워진 공동구매는 404 가 납니다. 한 건이 없다고 화면 전체를 실패로 두지 않고,
  // 하나도 못 받았을 때만 오류로 알립니다.
  const failedAll = likedIds.length > 0 && items.length === 0 && !loading
  const firstError = results.find((result) => result.isError)?.error

  return (
    <main className="wrap">
      <section className="section">
        <div className="sec-head">
          <div>
            <h2 className="sec-title">찜한 공동구매</h2>
          </div>
        </div>

        {likedIds.length === 0 ? (
          <EmptyState
            title="아직 찜한 상품이 없어요"
            action={{ to: '/group-buys', label: '상품 둘러보기' }}
          >
            취향에 맞는 상품을 찾으러 가볼까요?
          </EmptyState>
        ) : loading && items.length === 0 ? (
          <LoadingState label="찜한 공동구매를 불러오는 중입니다." />
        ) : failedAll ? (
          <ErrorState error={firstError} />
        ) : (
          <GroupBuyGrid items={items} />
        )}
      </section>
    </main>
  )
}


