import { useMemo, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router'

import { groupBuyApi } from '@/entities/groupBuy/api'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { productQueries } from '@/entities/product/queries'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { calculateAmounts, POLICY_DEFAULTS } from '@/shared/config/policy'
import { number, percent, won } from '@/shared/lib/format'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDebouncedValue } from '@/shared/lib/hooks/useDebouncedValue'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow, TotalRow } from '@/shared/ui/DataRow'
import { SelectField, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { Steps } from '@/shared/ui/Steps'
import { QueryBoundary } from '@/shared/ui/states'

const STEPS = ['상품 등록', '공동구매 생성'] as const

const PERIOD_OPTIONS = [3, 5, 7, 14].map((days) => ({
  value: String(days),
  label: `${String(days)}일`,
}))

const digits = (value: string): number => Number(value.replace(/\D/g, '') || '0')

export default function SellerGroupBuyNewPage() {
  useDocumentTitle('공동구매 생성')

  const [searchParams] = useSearchParams()
  const productId = searchParams.get('productId')

  const products = useQuery(productQueries.list({ size: 100 }))

  return (
    <section className="section">
      <div className="sec-head">
        <div>
          <h2 className="sec-title">공동구매 생성</h2>
          <p className="sec-sub">2단계 · 등록된 상품에 모집 조건을 설정합니다.</p>
        </div>
      </div>

      <Steps steps={STEPS} current={1} />

      <QueryBoundary query={products}>
        {(page) => {
          const selectable = page.items.filter((item) => item.linkedGroupBuyId === null)
          const initial = selectable.find((item) => item.id === productId) ?? selectable[0]

          if (initial === undefined) {
            return (
              <Panel className="mt24" title="선택할 수 있는 상품이 없습니다">
                <p className="para">
                  공동구매에 연결되지 않은 상품이 있어야 모집을 열 수 있습니다. 먼저 상품을 등록해
                  주세요.
                </p>
              </Panel>
            )
          }

          return <GroupBuyForm products={selectable} initialProductId={initial.id} />
        }}
      </QueryBoundary>
    </section>
  )
}

interface FormProduct {
  id: string
  name: string
  brand: string | null
  color: string | null
  categoryName: string
  price: number
  /** 등록한 이미지. 첫 장을 대표로 보여줍니다. */
  images: readonly string[]
  withdrawalRestrictionReason: string | null
}

function GroupBuyForm({
  products,
  initialProductId,
}: {
  products: readonly FormProduct[]
  initialProductId: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [selectedId, setSelectedId] = useState(initialProductId)
  const selected = products.find((item) => item.id === selectedId) ?? products[0]

  const [price, setPrice] = useState(String(selected?.price ?? 0))
  const [targetCount, setTargetCount] = useState('25')
  const [shippingFee, setShippingFee] = useState('4000')
  const [periodDays, setPeriodDays] = useState('7')

  const terms = useMemo(
    () => ({
      productPrice: digits(price),
      targetCount: digits(targetCount),
      shippingFee: digits(shippingFee),
    }),
    [price, targetCount, shippingFee],
  )

  /**
   * GRP-03. 입력이 멈춘 뒤 서버에 금액을 물어봅니다.
   *
   * 예전에는 policy.ts 의 같은 공식으로 화면에서 계산했는데, 서버와 어긋나면
   * 판매자가 본 금액과 실제 청구·정산액이 달라집니다. 금액은 서버가 확정합니다.
   */
  const debounced = useDebouncedValue(terms, 300)
  const canPreview =
    selectedId !== '' &&
    debounced.productPrice > 0 &&
    debounced.targetCount >= POLICY_DEFAULTS.minTargetCount

  const previewQuery = useQuery({
    ...groupBuyQueries.preview({ productId: selectedId, ...debounced }),
    enabled: canPreview,
  })
  const preview = previewQuery.data

  /**
   * 반품 1건이 생겼을 때의 정산액.
   * 서버 미리보기는 전원 구매확정 기준만 주므로 이 가정만 화면에서 계산하되,
   * 수수료율은 서버가 알려준 값을 씁니다.
   */
  const afterOneReturn = useMemo(() => {
    if (preview === undefined) return undefined
    return calculateAmounts(
      {
        productPrice: preview.productPrice,
        targetCount: preview.targetCount,
        shippingFee: preview.shippingFee,
      },
      {
        confirmedCount: Math.max(0, preview.targetCount - 1),
        platformFeeRate: preview.platformFeeRate,
      },
    )
  }, [preview])

  /** 서버 값이 아직 없으면 숫자 대신 줄표를 보여줍니다. */
  const amount = (value: number | undefined): string => (value === undefined ? '—' : won(value))

  const create = useMutation({
    mutationFn: () => {
      const now = new Date()
      const end = new Date(now.getTime() + digits(periodDays) * 86_400_000)
      return groupBuyApi.create({
        productId: selectedId,
        productPrice: terms.productPrice,
        targetCount: terms.targetCount,
        shippingFee: terms.shippingFee,
        recruitStartAt: now.toISOString(),
        recruitEndAt: end.toISOString(),
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.sellerGroupBuys.all })
      await queryClient.invalidateQueries({ queryKey: queryKeys.sellerProducts.all })
      await navigate('/seller', { replace: true })
    },
  })

  const isReady =
    terms.productPrice > 0 &&
    terms.targetCount >= POLICY_DEFAULTS.minTargetCount &&
    terms.shippingFee >= 0

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    create.mutate()
  }

  return (
    <form className="split mt24" onSubmit={submit}>
      <div>
        <Panel title="선택한 상품" description="상품 정보는 이 화면에서 수정할 수 없습니다">
          <SelectField
            label="상품"
            required
            value={selectedId}
            onChange={(event) => {
              const next = event.target.value
              setSelectedId(next)
              const product = products.find((item) => item.id === next)
              if (product) setPrice(String(product.price))
            }}
            options={products.map((product) => ({ value: product.id, label: product.name }))}
          />

          {selected && (
            <div className="order-item mt16">
              {/* 등록한 이미지를 보여줘야 어느 상품을 골랐는지 확인할 수 있습니다. */}
              <div className="thumb">
                {selected.images[0] !== undefined && (
                  <img src={selected.images[0]} alt={`${selected.name} 대표 이미지`} />
                )}
              </div>
              <div className="body">
                <div className="name">{selected.name}</div>
                <div className="small muted">
                  {[selected.brand, selected.categoryName, selected.color]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
                <div className="small mt8">
                  {selected.withdrawalRestrictionReason ?? '청약철회 제한 없음'}
                </div>
              </div>
            </div>
          )}
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
            hint={`${String(POLICY_DEFAULTS.minTargetCount)}명 이상 정수. 모집 완료 후에는 변경할 수 없습니다.`}
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

          <SelectField
            label="모집 기간"
            required
            value={periodDays}
            onChange={(event) => {
              setPeriodDays(event.target.value)
            }}
            options={PERIOD_OPTIONS}
          />
        </Panel>
      </div>

      <aside className="sticky">
        <Panel
          title="금액 구성"
          description={`${number(terms.productPrice)}원 ÷ ${String(terms.targetCount)}명`}
        >
          <DataRow label="상품 가격">{won(terms.productPrice)}</DataRow>
          <DataRow label="목표 모집 인원">{terms.targetCount}명</DataRow>
          <DataRow label="상품 분담금">{amount(preview?.unitShareAmount)}</DataRow>
          <DataRow label="배송비 (참여자 부담)">{won(terms.shippingFee)}</DataRow>
          <TotalRow label="개인 결제금액">
            {amount(preview && preview.unitShareAmount + preview.shippingFee)}
          </TotalRow>

          <div className="notice">
            <div className="notice-head">예상 정산 · 전원 구매확정 기준</div>
            <DataRow
              label={
                <>
                  상품 분담금 합계 <span className="small muted">절상 차액 포함</span>
                </>
              }
            >
              {amount(preview && preview.unitShareAmount * preview.targetCount)}
            </DataRow>
            <DataRow label="배송비 합계">
              {amount(preview && preview.shippingFee * preview.targetCount)}
            </DataRow>
            <DataRow
              label={
                <>
                  └ 절상 차액 <span className="small muted">판매자 귀속</span>
                </>
              }
            >
              {amount(preview?.roundingSurplus)}
            </DataRow>
            <DataRow
              label={
                <>
                  플랫폼 수수료{' '}
                  <span className="small muted">
                    분담금의 {preview ? percent(preview.platformFeeRate) : '—'}
                  </span>
                </>
              }
            >
              − {amount(preview?.estimatedPlatformFee)}
            </DataRow>
            <TotalRow label="예상 정산금액">{amount(preview?.estimatedSettlementAmount)}</TotalRow>

            <p className="small muted mt16" style={{ lineHeight: 1.8, marginBottom: 0 }}>
              반품이 발생하면 해당 건은 정산에서 제외됩니다.
              <br />
              반품 1건 발생 시 정산금액 <b>{amount(afterOneReturn?.estimatedSettlementAmount)}</b>
            </p>
          </div>

          {previewQuery.isError && (
            <p className="ai-hint" role="alert">
              금액을 불러오지 못했습니다. {toMessage(previewQuery.error)}
            </p>
          )}

          {create.isError && (
            <p className="ai-hint" role="alert">
              {toMessage(create.error)}
            </p>
          )}

          <Button type="submit" block className="mt16" disabled={!isReady || create.isPending}>
            {create.isPending ? '생성 중…' : '공동구매 생성하기'}
          </Button>
          <p className="small muted mt8" style={{ textAlign: 'center', marginBottom: 0 }}>
            생성 후에는 참여자가 1명이라도 있으면 조건을 변경할 수 없습니다.
          </p>
        </Panel>
      </aside>
    </form>
  )
}
