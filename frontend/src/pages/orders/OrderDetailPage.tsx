import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'

import { participationQueries } from '@/entities/participation/queries'
import { OrderDetail } from '@/features/orders/OrderDetail'
import { groupBuyNo } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { QueryBoundary } from '@/shared/ui/states'

/**
 * 주문 한 건의 상세.
 *
 * 전에는 목록에서 아코디언으로 펼쳤습니다. 별도 주소로 떼어내면 뒤로가기와
 * 새로고침이 제자리를 찾고, 구매확정·반품 화면에 다녀와도 보던 건으로 돌아옵니다.
 */
export default function OrderDetailPage() {
  const { participationId = '' } = useParams()
  const query = useQuery(participationQueries.detail(participationId))

  useDocumentTitle(query.data?.productName ?? '주문 상세')

  return (
    /* 읽는 화면이라 본문을 좁힙니다. 폭이 1200px 이면 한 줄이 너무 길어
       레이블과 값이 눈으로 이어지지 않습니다 */
    <main className="wrap narrow">
      <section className="section tight">
        <div className="sec-head">
          <div>
            <Link className="sec-back" to="/orders">
              주문/참여 현황
            </Link>
            <h2 className="sec-title">{query.data?.productName ?? '주문 상세'}</h2>
            {query.data && (
              <p className="sec-sub">공동구매 {groupBuyNo(query.data.groupBuyId)}</p>
            )}
          </div>
        </div>

        <QueryBoundary query={query}>{(detail) => <OrderDetail item={detail} />}</QueryBoundary>
      </section>
    </main>
  )
}


