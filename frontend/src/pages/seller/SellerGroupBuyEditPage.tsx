import { useMemo, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import { groupBuyApi } from '@/entities/groupBuy/api'
import type { SellerGroupBuyDetail } from '@/entities/groupBuy/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { POLICY_DEFAULTS } from '@/shared/config/policy'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { groupBuyNo, number, won } from '@/shared/lib/format'
import { useDebouncedValue } from '@/shared/lib/hooks/useDebouncedValue'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button, ButtonLink } from '@/shared/ui/Button'
import { DataRow, TotalRow } from '@/shared/ui/DataRow'
import { TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { QueryBoundary } from '@/shared/ui/states'

const digits = (value: string): number => Number(value.replace(/\D/g, '') || '0')

/** `<input type="date">` 가 요구하는 YYYY-MM-DD 로 자릅니다. */
const dateInputValue = (iso: string): string => iso.slice(0, 10)

export default function SellerGroupBuyEditPage() {
  useDocumentTitle('공동구매 수정')

  const { groupBuyId = '' } = useParams()
  const detail = useQuery(groupBuyQueries.sellerDetail(groupBuyId))

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">공동구매 {groupBuyNo(groupBuyId)} 수정</h2>
          <p className="sec-sub">모집 조건을 바꿉니다. 상품은 이 화면에서 변경할 수 없습니다.</p>
        </div>
        <ButtonLink variant="ghost" to={`/seller/group-buys/${groupBuyId}`}>
          상세로
        </ButtonLink>
      </div>

      <QueryBoundary query={detail}>
        {(found) =>
          found.canEdit ? (
            <EditForm found={found} />
          ) : (
            /* 백엔드도 같은 조건을 막습니다(GroupBuy.canEditRecruitment). 화면에서 먼저
               알려주지 않으면 다 채워 넣고 저장할 때가 되어서야 거절당합니다. */
            <Panel className="mt24" title="지금은 수정할 수 없습니다">
              <p className="para">
                참여자나 진행 중인 결제가 있는 공동구매는 모집 조건을 바꿀 수 없습니다. 이미 결제한
                참여자의 분담금이 달라지기 때문입니다.
              </p>
              <DataRow label="현재 참여">
                {found.paidCount} / {found.targetCount}명
              </DataRow>
              <ButtonLink
                variant="ghost"
                block
                className="mt16"
                to={`/seller/group-buys/${groupBuyId}`}
              >
                상세로 돌아가기
              </ButtonLink>
            </Panel>
          )
        }
      </QueryBoundary>
    </section>
  )
}

function EditForm({ found }: { found: SellerGroupBuyDetail }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [price, setPrice] = useState(String(found.productPrice))
  const [targetCount, setTargetCount] = useState(String(found.targetCount))
  const [shippingFee, setShippingFee] = useState(String(found.shippingFee))
  const [recruitEndAt, setRecruitEndAt] = useState(dateInputValue(found.recruitEndAt))

  const terms = useMemo(
    () => ({
      productPrice: digits(price),
      targetCount: digits(targetCount),
      shippingFee: digits(shippingFee),
    }),
    [price, targetCount, shippingFee],
  )

  /**
   * GRP-03 으로 금액을 서버에 다시 물어봅니다. 생성 화면과 같은 이유입니다 —
   * 화면에서 계산하면 서버가 확정한 분담금과 어긋날 수 있습니다.
   */
  const debounced = useDebouncedValue(terms, 300)
  const canPreview =
    debounced.productPrice > 0 && debounced.targetCount >= POLICY_DEFAULTS.minTargetCount

  const previewQuery = useQuery({
    ...groupBuyQueries.preview({ productId: found.productId, ...debounced }),
    enabled: canPreview,
  })
  const preview = previewQuery.data

  /** 서버 값이 아직 없으면 숫자 대신 줄표를 보여줍니다. */
  const amount = (value: number | undefined): string => (value === undefined ? '—' : won(value))

  const save = useMutation({
    mutationFn: () =>
      groupBuyApi.update(found.id, {
        productPrice: terms.productPrice,
        targetCount: terms.targetCount,
        shippingFee: terms.shippingFee,
        // 날짜만 고르게 하고 시각은 그날 끝으로 맞춥니다.
        recruitEndAt: new Date(`${recruitEndAt}T23:59:59Z`).toISOString(),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.sellerGroupBuys.all })
      await navigate(`/seller/group-buys/${found.id}`, { replace: true })
    },
  })

  const changed =
    terms.productPrice !== found.productPrice ||
    terms.targetCount !== found.targetCount ||
    terms.shippingFee !== found.shippingFee ||
    recruitEndAt !== dateInputValue(found.recruitEndAt)

  const isReady =
    changed &&
    terms.productPrice > 0 &&
    terms.targetCount >= POLICY_DEFAULTS.minTargetCount &&
    recruitEndAt !== ''

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    save.mutate()
  }

  return (
    <form className="split mt24" onSubmit={submit}>
      <div>
        <Panel title="상품" description="상품을 바꾸려면 공동구매를 취소하고 새로 여세요">
          <DataRow label="상품명">{found.productName}</DataRow>
          <DataRow label="현재 조건">
            {won(found.productPrice)} · {found.targetCount}명 · 배송비 {won(found.shippingFee)}
          </DataRow>
        </Panel>

        <Panel
          className="mt16"
          title="모집 조건"
          description="입력하면 오른쪽 금액이 즉시 다시 계산됩니다"
        >
          <TextField
            label="상품 가격"
            required
            inputMode="numeric"
            value={number(terms.productPrice)}
            onChange={(event) => {
              setPrice(event.target.value)
            }}
            hint="공동구매 1회분 총액입니다. 이 금액을 목표 인원으로 나눠 분담금이 정해집니다."
          />

          <TextField
            label="목표 모집 인원"
            required
            inputMode="numeric"
            value={targetCount}
            onChange={(event) => {
              setTargetCount(event.target.value.replace(/\D/g, ''))
            }}
            hint={`${String(POLICY_DEFAULTS.minTargetCount)}명 이상 정수입니다.`}
            error={
              terms.targetCount > 0 && terms.targetCount < POLICY_DEFAULTS.minTargetCount
                ? `목표 인원은 ${String(POLICY_DEFAULTS.minTargetCount)}명 이상이어야 합니다.`
                : undefined
            }
          />

          <TextField
            label="1인 배송비"
            required
            inputMode="numeric"
            value={number(terms.shippingFee)}
            onChange={(event) => {
              setShippingFee(event.target.value)
            }}
            hint="참여자가 개별 부담하며 정산 시 합산되어 지급됩니다."
          />

          <TextField
            label="모집 마감일"
            required
            type="date"
            value={recruitEndAt}
            onChange={(event) => {
              setRecruitEndAt(event.target.value)
            }}
            hint="선택한 날짜의 끝에 모집이 닫힙니다."
          />
        </Panel>
      </div>

      <aside className="sticky">
        <Panel
          title="금액 구성"
          description={`${number(terms.productPrice)}원 ÷ ${String(terms.targetCount)}명`}
        >
          <DataRow label="상품 분담금">{amount(preview?.unitShareAmount)}</DataRow>
          <DataRow label="배송비 (참여자 부담)">{won(terms.shippingFee)}</DataRow>
          <TotalRow label="개인 결제금액">
            {amount(preview && preview.unitShareAmount + preview.shippingFee)}
          </TotalRow>

          <DataRow className="mt16" label="변경 전 분담금">
            {won(found.unitShareAmount)}
          </DataRow>

          {save.isError && <p className="ai-hint fail">{toMessage(save.error)}</p>}

          <Button type="submit" block className="mt16" disabled={!isReady || save.isPending}>
            {save.isPending ? '저장하는 중…' : '수정 저장'}
          </Button>

          <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
            참여자가 한 명이라도 생기면 더 이상 수정할 수 없습니다.
          </p>
        </Panel>
      </aside>
    </form>
  )
}
