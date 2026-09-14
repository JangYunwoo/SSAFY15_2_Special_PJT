import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import { participationApi } from '@/entities/participation/api'
import type { DisputeRequest, ParticipationDetail } from '@/entities/participation/model'
import { participationQueries } from '@/entities/participation/queries'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { date, groupBuyNo, number, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button, ButtonLink } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { TextAreaField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { RadioGroup } from '@/shared/ui/RadioGroup'
import { QueryBoundary } from '@/shared/ui/states'

type DisputeType = DisputeRequest['type']

const TYPE_OPTIONS: { value: DisputeType; label: string }[] = [
  { value: 'NOT_DELIVERED', label: '상품을 받지 못했습니다' },
  { value: 'ITEM_MISMATCH', label: '주문과 다른 상품이 왔습니다' },
]

/**
 * 미배송·상품 불일치 이의제기 (CFM-02).
 *
 * 판매자가 배송완료로 넘겼지만 실제로 받지 못했거나 다른 물건이 온 경우입니다.
 * 반품(RTN-02)은 상품을 받은 뒤 돌려보내는 절차라 이 상황에 맞지 않아,
 * 자동 구매확정을 멈추는 이 경로를 따로 둡니다.
 */
export default function DisputePage() {
  useDocumentTitle('이의제기')

  const { participationId = '' } = useParams()
  const query = useQuery(participationQueries.detail(participationId))

  return (
    <main className="wrap">
      <section className="narrow">
        <h2>이의제기</h2>
        <p>배송완료로 표시됐지만 상품에 문제가 있다면 알려주세요.</p>

        <QueryBoundary query={query}>
          {(detail) => <DisputeForm participationId={participationId} detail={detail} />}
        </QueryBoundary>
      </section>
    </main>
  )
}

function DisputeForm({
  participationId,
  detail,
}: {
  participationId: string
  detail: ParticipationDetail
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [type, setType] = useState<DisputeType>('NOT_DELIVERED')
  const [detailReason, setDetailReason] = useState('')

  const submit = useMutation({
    mutationFn: () => participationApi.dispute(participationId, { type, detailReason }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.participations.all })
      await navigate('/orders', { replace: true })
    },
  })

  return (
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

      <Panel className="mt16" title="어떤 문제인가요" description="한 가지를 골라주세요">
        <RadioGroup legend="이의제기 유형" value={type} onChange={setType} options={TYPE_OPTIONS} />

        <div className="mt16">
          <TextAreaField
            label="상세 내용"
            required
            hint="언제 무엇을 확인했는지 구체적으로 적어주세요. 판매자와 관리자가 함께 봅니다."
            placeholder="예) 배송완료로 바뀐 날 문 앞과 경비실을 확인했지만 물건이 없었습니다."
            value={detailReason}
            onChange={(event) => {
              setDetailReason(event.target.value)
            }}
          />
        </div>
      </Panel>

      <Panel className="mt16" title="접수하면 이렇게 됩니다" description="처리 절차">
        <DataRow label="자동 구매확정">검토가 끝날 때까지 보류</DataRow>
        <DataRow label="에스크로">보관 유지 · 판매자 정산 개시 안 함</DataRow>
        <DataRow label="청약철회 기한">
          수령 다음 날부터 {POLICY_DEFAULTS.autoConfirmDays}일 이내
        </DataRow>
        {detail.autoConfirmAt !== null && (
          <DataRow label="현재 자동 확정 예정일">{date(detail.autoConfirmAt)}</DataRow>
        )}
      </Panel>

      <div className="box mt16" style={{ fontSize: 12, lineHeight: 1.8 }}>
        상품을 받았는데 하자가 있거나 마음이 바뀐 경우라면 이의제기가 아니라 반품을 신청하세요.
        이의제기는 배송 자체가 이뤄지지 않았거나 주문과 다른 상품이 온 경우를 위한 절차입니다.
      </div>

      {submit.isError && (
        <p className="ai-hint mt16" role="alert">
          {toMessage(submit.error)}
        </p>
      )}

      <Button
        block
        className="mt24"
        disabled={detailReason.trim() === '' || submit.isPending}
        onClick={() => {
          submit.mutate()
        }}
      >
        {submit.isPending ? '접수 중…' : '이의제기 접수'}
      </Button>
      <ButtonLink variant="ghost" className="mt8" style={{ display: 'block' }} to="/orders">
        돌아가기
      </ButtonLink>
    </>
  )
}
