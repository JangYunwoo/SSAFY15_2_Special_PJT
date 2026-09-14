import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import { RETURN_REASON_LABEL, type ReturnReasonType } from '@/entities/model'
import type { ParticipationDetail } from '@/entities/participation/model'
import { participationQueries } from '@/entities/participation/queries'
import { returnApi } from '@/entities/returnRequest/api'
import type { ReturnPreview } from '@/entities/returnRequest/model'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import { date, groupBuyNo, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow, TotalRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { RadioGroup } from '@/shared/ui/RadioGroup'
import { TextAreaField, TextField } from '@/shared/ui/Field'
import { QueryBoundary } from '@/shared/ui/states'

const REASON_OPTIONS = (['CHANGE_OF_MIND', 'DEFECT'] as const).map(
  (value) => ({ value, label: RETURN_REASON_LABEL[value] }),
)

export default function ReturnRequestPage() {
  useDocumentTitle('반품 신청')

  const { participationId = '' } = useParams()
  const query = useQuery(participationQueries.detail(participationId))

  return (
    <main className="wrap">
      <section className="section" style={{ paddingTop: 40 }}>
        <QueryBoundary query={query}>
          {(detail) => <ReturnForm participationId={participationId} detail={detail} />}
        </QueryBoundary>
      </section>
    </main>
  )
}

function ReturnForm({
  participationId,
  detail,
}: {
  participationId: string
  detail: ParticipationDetail
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [reasonType, setReasonType] = useState<ReturnReasonType>('CHANGE_OF_MIND')
  const [detailReason, setDetailReason] = useState('')
  const [pickupAddress, setPickupAddress] = useState(`${detail.address1} ${detail.address2}`.trim())
  const [preview, setPreview] = useState<ReturnPreview | null>(null)

  /**
   * RTN-01. 사유에 따라 환불액과 반품비 부담 주체가 달라지므로
   * 금액은 직접 계산하지 않고 사유가 바뀔 때마다 서버에 다시 물어봅니다.
   */
  const previewMutation = useMutation({
    mutationFn: (reason: ReturnReasonType) =>
      returnApi.preview(participationId, { reasonType: reason, detailReason: '' }),
    onSuccess: setPreview,
  })

  useEffect(() => {
    previewMutation.mutate(reasonType)
    // reasonType 변화에만 반응하면 됩니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reasonType])

  const submit = useMutation({
    mutationFn: () =>
      returnApi.create(participationId, {
        reasonType,
        detailReason,
        pickupZipcode: detail.zipcode,
        pickupAddress1: pickupAddress,
        pickupAddress2: '',
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.participations.all })
      await navigate('/orders', { replace: true })
    },
  })

  const isChangeOfMind = reasonType === 'CHANGE_OF_MIND'

  return (
    <div className="split">
      <div>
        <Panel>
          <div className="order-item">
            <div className="thumb" />
            <div className="body">
              <div className="name">{detail.productName}</div>
              <div className="small muted">
                공동구매 {groupBuyNo(detail.groupBuyId)} · {detail.sellerName}
              </div>
              <div className="small mt8">결제 금액 {won(detail.totalAmount)}</div>
            </div>
          </div>
        </Panel>

        <Panel
          className="mt16"
          title="반품 사유"
          description="사유에 따라 반품 배송비 부담 주체가 달라집니다"
        >
          <RadioGroup
            legend="반품 사유"
            options={REASON_OPTIONS}
            value={reasonType}
            onChange={setReasonType}
          />

          <TextAreaField
            label="상세 사유"
            required
            placeholder="상품 상태와 반품 사유를 적어주세요."
            value={detailReason}
            onChange={(event) => {
              setDetailReason(event.target.value)
            }}
          />

          <TextField
            label="수거지"
            value={pickupAddress}
            onChange={(event) => {
              setPickupAddress(event.target.value)
            }}
          />

          <div className="box mt16" style={{ fontSize: 12, lineHeight: 1.8 }}>
            {isChangeOfMind ? (
              <>
                단순변심은 최초 배송비를 환불하지 않고, 반품 배송비를 구매자가 부담합니다. 상품이
                재판매 가능한 상태여야 합니다.
              </>
            ) : (
              <>
                상품 하자·오배송은 최초 배송비까지 전액 환불되고 반품 배송비는 판매자가 부담합니다.
                판매자가 {POLICY_DEFAULTS.returnApprovalBusinessDays}영업일 안에 처리하지 않으면
                자동 승인됩니다.
              </>
            )}
          </div>
        </Panel>
      </div>

      <aside className="sticky">
        <Panel title="예상 환불 금액" description="반품 사유 기준으로 계산됩니다">
          {preview === null ? (
            <p className="small muted">환불 금액을 계산하는 중입니다.</p>
          ) : (
            <>
              <DataRow label="결제 금액">{won(preview.paymentAmount)}</DataRow>
              <DataRow label="환불 대상 분담금">{won(preview.refundableShareAmount)}</DataRow>
              <DataRow label="최초 배송비 환불">
                {preview.refundableOriginalShippingFee > 0
                  ? won(preview.refundableOriginalShippingFee)
                  : '환불 없음'}
              </DataRow>
              <DataRow
                label={
                  <>
                    반품 배송비{' '}
                    <span className="small muted">
                      {preview.shippingFeeBearer === 'BUYER' ? '구매자 부담' : '판매자 부담'}
                    </span>
                  </>
                }
              >
                {preview.shippingFeeBearer === 'BUYER'
                  ? `− ${won(preview.returnShippingFee)}`
                  : won(0)}
              </DataRow>
              <TotalRow label="예상 환불액">{won(preview.refundAmount)}</TotalRow>

              <DataRow label="신청 기한">{date(preview.applicationDeadline)}</DataRow>

              {!preview.eligible && preview.ineligibleReason !== null && (
                <p className="ai-hint" role="alert">
                  {preview.ineligibleReason}
                </p>
              )}
            </>
          )}

          {submit.isError && (
            <p className="ai-hint" role="alert">
              {toMessage(submit.error)}
            </p>
          )}

          <Button
            block
            className="mt16"
            disabled={submit.isPending || detailReason.trim() === '' || !preview?.eligible}
            onClick={() => {
              submit.mutate()
            }}
          >
            {submit.isPending ? '신청 중…' : '반품 신청하기'}
          </Button>
          <p className="small muted mt8" style={{ textAlign: 'center', marginBottom: 0 }}>
            신청 후에는 판매자 승인 절차가 진행됩니다.
          </p>
        </Panel>
      </aside>
    </div>
  )
}
