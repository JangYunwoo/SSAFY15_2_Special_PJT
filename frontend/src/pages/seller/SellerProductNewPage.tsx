import { useState, type ChangeEvent } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'

import { categoryQueries } from '@/entities/category/queries'
import { productApi } from '@/entities/product/api'
import { productQueries } from '@/entities/product/queries'
import { ImageUploader, type PickedImage } from '@/features/product/ImageUploader'
import {
  aiHintText,
  useAiExtraction,
  type AiExtractionResult,
} from '@/features/product/useAiExtraction'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { number } from '@/shared/lib/format'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { FieldRow, SelectField, TextAreaField, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { RadioGroup } from '@/shared/ui/RadioGroup'
import { Steps } from '@/shared/ui/Steps'

const STEPS = ['상품 등록', '공동구매 생성'] as const

interface FormState {
  name: string
  brand: string
  modelName: string
  color: string
  categoryId: string
  price: string
  description: string
}

const INITIAL: FormState = {
  name: '',
  brand: '',
  modelName: '',
  color: '',
  categoryId: '',
  price: '',
  description: '',
}

export default function SellerProductNewPage() {
  useDocumentTitle('상품 등록')

  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const categories = useQuery(categoryQueries.tree())
  const withdrawalReasons = useQuery(productQueries.withdrawalReasons())

  const [images, setImages] = useState<PickedImage[]>([])
  const [form, setForm] = useState<FormState>(INITIAL)
  const [restricted, setRestricted] = useState<'NONE' | 'RESTRICTED'>('NONE')
  const [reasonId, setReasonId] = useState('')
  const [ai, setAi] = useState<AiExtractionResult | null>(null)

  const extraction = useAiExtraction((result) => {
    setAi(result)
    // AI 값은 비어 있는 칸만 채우고, 판매자가 이미 입력한 값은 건드리지 않습니다.
    setForm((current) => ({
      name: current.name || (result.values.name ?? ''),
      brand: current.brand || (result.values.brand ?? ''),
      modelName: current.modelName || (result.values.modelName ?? ''),
      color: current.color || (result.values.color ?? ''),
      categoryId: current.categoryId || (result.values.categoryId ?? ''),
      price: current.price || (result.values.price ?? ''),
      description: current.description || (result.values.description ?? ''),
    }))
  })

  const create = useMutation({
    mutationFn: () =>
      productApi.create({
        name: form.name,
        brand: form.brand,
        modelName: form.modelName,
        color: form.color,
        categoryId: form.categoryId,
        price: Number(form.price.replace(/\D/g, '')),
        description: form.description,
        // 이미지 업로드 엔드포인트가 확정되면 업로드 후 URL 을 넣습니다.
        imageUrls: [],
        aiExtracted: ai !== null,
        withdrawalRestrictionReasonId: restricted === 'RESTRICTED' ? reasonId || null : null,
      }),
    onSuccess: async (product) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.sellerProducts.all })
      await navigate(`/seller/group-buys/new?productId=${product.id}`)
    },
  })

  /** 입력 하나를 form 상태의 같은 이름 필드에 반영합니다. */
  const set =
    (key: keyof FormState) =>
    (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>): void => {
      setForm((current) => ({ ...current, [key]: event.target.value }))
    }

  const isReady =
    form.name.trim() !== '' &&
    form.description.trim() !== '' &&
    form.categoryId !== '' &&
    Number(form.price.replace(/\D/g, '')) > 0 &&
    images.length > 0

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    create.mutate()
  }

  return (
    <section className="section">
      <div className="sec-head">
        <div>
          <h2 className="sec-title">상품 등록</h2>
          <p className="sec-sub">
            1단계 · 상품 정보를 등록합니다. 모집 조건은 다음 단계에서 설정합니다.
          </p>
        </div>
      </div>

      <Steps steps={STEPS} current={0} />

      <form className="split mt24" onSubmit={submit}>
        <div>
          <Panel
            title="상품 이미지"
            description="대표 이미지 1장 필수 · 추가 이미지는 선택 (최대 4장)"
          >
            <ImageUploader images={images} onChange={setImages} />

            <div className="ai-bar mt16">
              <div>
                <div className="ai-title">AI 상품 정보 자동 추출</div>
                <div className="small muted">
                  {extraction.isPending
                    ? '이미지를 분석하고 있습니다…'
                    : extraction.isError
                      ? toMessage(extraction.error)
                      : ai !== null
                        ? (ai.warnings[0] ?? '추출된 값을 확인하고 수정해 주세요.')
                        : '대표 이미지를 올린 뒤 분석을 요청하면 아래 항목이 자동으로 채워집니다.'}
                </div>
              </div>
              <Button
                disabled={images.length === 0 || extraction.isPending}
                onClick={() => {
                  extraction.mutate(images.map((image) => image.file))
                }}
              >
                {extraction.isPending ? '분석 중…' : 'AI로 정보 채우기'}
              </Button>
            </div>
          </Panel>

          <Panel
            className="mt16"
            title="상품 정보"
            description="구매자에게 표시되는 내용 · AI가 채운 값도 반드시 검수해 주세요"
          >
            <TextField
              label="상품명"
              required
              placeholder="예) MacBook Air M4 16GB"
              value={form.name}
              onChange={set('name')}
              hint={aiHintText(ai?.hints.name)}
            />

            <FieldRow className="mt8">
              <TextField
                label="브랜드"
                placeholder="예) Apple"
                value={form.brand}
                onChange={set('brand')}
                hint={aiHintText(ai?.hints.brand)}
              />
              <TextField
                label="모델명"
                placeholder="예) MGN63KH/A"
                value={form.modelName}
                onChange={set('modelName')}
                hint={aiHintText(ai?.hints.modelName)}
              />
            </FieldRow>

            <FieldRow className="mt8">
              <SelectField
                label="카테고리"
                required
                placeholder="선택"
                value={form.categoryId}
                onChange={set('categoryId')}
                options={(categories.data ?? []).map((category) => ({
                  value: category.categoryId,
                  label: category.name,
                }))}
                hint={aiHintText(ai?.hints.categoryId)}
              />
              <TextField
                label="색상"
                placeholder="예) 미드나이트"
                value={form.color}
                onChange={set('color')}
                hint={aiHintText(ai?.hints.color)}
              />
            </FieldRow>

            <TextField
              label="상품 가격"
              required
              inputMode="numeric"
              placeholder="공동구매 1회분 총액"
              value={form.price === '' ? '' : number(Number(form.price.replace(/\D/g, '')))}
              onChange={(event) => {
                setForm((current) => ({
                  ...current,
                  price: event.target.value.replace(/\D/g, ''),
                }))
              }}
              hint={aiHintText(ai?.hints.price) ?? '공동구매 1회분 총액을 입력합니다.'}
            />

            <TextAreaField
              label="상품 설명"
              required
              placeholder="상품 구성, 보증, 배송 조건 등을 입력해 주세요."
              value={form.description}
              onChange={set('description')}
              hint={aiHintText(ai?.hints.description)}
            />
          </Panel>

          <Panel
            className="mt16"
            title="청약철회 제한"
            description="법정 청약철회권은 제한할 수 없으며, 예외 사유에 해당할 때만 지정합니다"
          >
            <RadioGroup
              legend="청약철회 제한"
              value={restricted}
              onChange={setRestricted}
              options={[
                { value: 'NONE', label: '제한 없음' },
                { value: 'RESTRICTED', label: '제한 상품' },
              ]}
            />

            <SelectField
              label="제한 사유"
              placeholder="사유 선택"
              disabled={restricted === 'NONE'}
              value={reasonId}
              onChange={(event) => {
                setReasonId(event.target.value)
              }}
              options={(withdrawalReasons.data ?? []).map((reason) => ({
                value: reason.id,
                label: reason.description,
              }))}
            />

            <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
              제한 상품으로 지정하면 관리자 검토를 거친 뒤 공동구매 상세 화면에 사유가 함께
              표시됩니다. 제한 상품이라도 상품 하자·오배송·표시광고 불일치의 경우에는 반품이
              가능합니다.
            </p>
          </Panel>

          {create.isError && (
            <p className="ai-hint mt16" role="alert">
              {toMessage(create.error)}
            </p>
          )}

          <Button type="submit" block className="mt24" disabled={!isReady || create.isPending}>
            {create.isPending ? '등록 중…' : '상품 등록하고 공동구매 만들기'}
          </Button>
        </div>

        <aside className="sticky">
          <Panel title="등록 안내" description="등록 후 공동구매를 개설합니다">
            <DataRow label="필수 항목">상품명 · 설명 · 가격 · 카테고리</DataRow>
            <DataRow label="이미지">1장 이상 · JPG/PNG</DataRow>
            <DataRow label="상품 가격">공동구매 1회분 총액</DataRow>
            <DataRow label="수정 가능 시점">공동구매 연결 전까지</DataRow>

            <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
              AI 추출 결과는 정확도를 보장하지 않습니다. 판매자가 확인해야 등록이 완료되며, 분석에
              실패한 항목은 비워둔 채 직접 입력할 수 있습니다.
            </p>
          </Panel>
        </aside>
      </form>
    </section>
  )
}
