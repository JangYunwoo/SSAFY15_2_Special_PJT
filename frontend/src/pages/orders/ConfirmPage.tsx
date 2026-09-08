import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import { participationApi } from '@/entities/participation/api'
import { participationQueries } from '@/entities/participation/queries'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { date, groupBuyNo, number, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button, ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { QueryBoundary } from '@/shared/ui/states'

export default function ConfirmPage() {
  useDocumentTitle('구매확정')

  const { participationId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const query = useQuery(participationQueries.detail(participationId))

  const confirm = useMutation({
    mutationFn: () => participationApi.confirm(participationId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.participations.all })
      await navigate('/orders', { replace: true })
    },
  })

  return (
    <main className="wrap">
      <section className="narrow">
        <h2>구매확정</h2>
        <p>상품을 정상적으로 받으셨는지 확인해 주세요.</p>

        <QueryBoundary query={query}>
          {(detail) => (
            <>
              <Panel>
                <div className="order-item">
                  <div className="thumb" />
                  <div className="body">
                    <div className="name">{detail.productName}</div>
                    <div className="small muted">
                      공동구매 {groupBuyNo(detail.groupBuyId)} · {detail.sellerName}
                    </div>
                    <div className="small mt8">
                      {won(detail.totalAmount)}{' '}
                      <span className="muted">
                        분담금 {number(detail.shareAmount)} + 배송비 {number(detail.shippingFee)}
                      </span>
                    </div>
                  </div>
                </div>
              </Panel>

              <Panel
                className="mt16"
                title="확정하면 이렇게 됩니다"
                description="구매확정은 취소할 수 없습니다"
              >
                <DataRow label="에스크로">해제 → 판매자 정산 개시</DataRow>
                <DataRow label="정산 가능 시점">
                  배송완료 후 {POLICY_DEFAULTS.settlementEligibleDays}일 경과 시
                </DataRow>
                <DataRow label="청약철회">
                  수령 다음 날부터 {POLICY_DEFAULTS.autoConfirmDays}일 이내 가능
                </DataRow>
                <DataRow label="확정 후 반품">관리자 검토 후 처리</DataRow>
                {detail.withdrawalDeadlineAt !== null && (
                  <DataRow label="청약철회 기한">{date(detail.withdrawalDeadlineAt)}</DataRow>
                )}
              </Panel>

              <div className="box mt16" style={{ fontSize: 12, lineHeight: 1.8 }}>
                구매확정 이후에도 청약철회 기간 안에는 반품을 신청할 수 있습니다. 다만 확정 후
                반품은 관리자 검토를 거치므로, 상품에 문제가 있다면 확정 전에 신청하는 편이 처리
                절차가 짧습니다.
              </div>

              {confirm.isError && (
                <p className="ai-hint mt16" role="alert">
                  {toMessage(confirm.error)}
                </p>
              )}

              <Button
                block
                className="mt24"
                disabled={confirm.isPending || !detail.canConfirm}
                onClick={() => {
                  confirm.mutate()
                }}
              >
                {confirm.isPending ? '처리 중…' : '확인했습니다 · 구매확정'}
              </Button>
              <ButtonLink variant="ghost" className="mt8" style={{ display: 'block' }} to="/orders">
                돌아가기
              </ButtonLink>
            </>
          )}
        </QueryBoundary>
      </section>
    </main>
  )
}
