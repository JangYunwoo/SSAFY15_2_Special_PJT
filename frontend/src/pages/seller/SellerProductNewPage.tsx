import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'

import { productApi } from '@/entities/product/api'
import { ImageUploader, type PickedImage } from '@/features/product/ImageUploader'
import { ProductFields } from '@/features/product/ProductFields'
import {
  EMPTY_PRODUCT_FORM,
  isProductFormReady,
  optionalText,
  type ProductFormState,
} from '@/features/product/productForm'
import { aiProgressText } from '@/features/product/aiProgress'
import { useAiExtraction, type AiExtractionResult } from '@/features/product/useAiExtraction'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { useElapsedSeconds } from '@/shared/lib/hooks/useElapsedSeconds'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { Steps } from '@/shared/ui/Steps'

const STEPS = ['상품 등록', '공동구매 생성'] as const

export default function SellerProductNewPage() {
  useDocumentTitle('상품 등록')

  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [images, setImages] = useState<PickedImage[]>([])
  const [form, setForm] = useState<ProductFormState>(EMPTY_PRODUCT_FORM)
  const [ai, setAi] = useState<AiExtractionResult | null>(null)
  /** 분석 시작 시각. 경과 초를 보여주는 데만 씁니다 */
  const [analysisStartedAt, setAnalysisStartedAt] = useState<number | null>(null)

  const patch = (next: Partial<ProductFormState>) => {
    setForm((current) => ({ ...current, ...next }))
  }

  const extraction = useAiExtraction((result) => {
    setAi(result)
    // AI 값은 비어 있는 칸만 채우고, 판매자가 이미 입력한 값은 건드리지 않습니다.
    setForm((current) => ({
      ...current,
      name: current.name || (result.values.name ?? ''),
      brand: current.brand || (result.values.brand ?? ''),
      modelName: current.modelName || (result.values.modelName ?? ''),
      color: current.color || (result.values.color ?? ''),
      categoryId: current.categoryId || (result.values.categoryId ?? ''),
      price: current.price || (result.values.price ?? ''),
      description: current.description || (result.values.description ?? ''),
    }))
  })

  // 분석 중일 때만 시계를 구독합니다. 끝난 뒤에도 구독하면 이 화면이 1초마다
  // 의미 없이 다시 그려집니다.
  const elapsedSeconds = useElapsedSeconds(extraction.isPending ? analysisStartedAt : null)

  const create = useMutation({
    mutationFn: () =>
      productApi.create(
        {
          name: form.name,
          brand: optionalText(form.brand),
          modelName: optionalText(form.modelName),
          color: optionalText(form.color),
          categoryId: form.categoryId,
          price: Number(form.price),
          description: form.description,
          // 첫 장이 대표 이미지입니다 (ImageUploader 규칙).
          thumbnailIndex: 0,
          aiExtracted: ai !== null,
          withdrawalRestrictionReasonId:
            form.restricted === 'RESTRICTED' ? form.reasonId || null : null,
          restrictionEvidenceUrl:
            form.restricted === 'RESTRICTED' ? form.evidenceUrl || null : null,
        },
        images.map((image) => image.file),
      ),
    onSuccess: async (product) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.sellerProducts.all })
      await navigate(`/seller/group-buys/new?productId=${product.id}`)
    },
  })

  const isReady = isProductFormReady(form) && images.length > 0

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
            description="대표 이미지 1장 필수 · 추가 이미지는 선택 (최대 3장)"
          >
            <ImageUploader images={images} onChange={setImages} />

            <div className="ai-bar mt16">
              <div>
                <div className="ai-title">AI 상품 정보 자동 추출</div>
                {extraction.isPending ? (
                  /*
                   * 1분 가까이 걸리는 작업이라 글자만 두면 멈춘 것처럼 보입니다.
                   * 도는 표시로 진행 중임을 알리고, 경과 초로 실제로 흐르고 있음을
                   * 보여줍니다. role=status 로 읽어주는 도구에도 전달합니다.
                   */
                  <div className="small muted ai-progress" role="status" aria-live="polite">
                    <span className="ai-spin" aria-hidden="true" />
                    <span>{aiProgressText(elapsedSeconds)}</span>
                  </div>
                ) : (
                  <div className="small muted">
                    {extraction.isError
                      ? toMessage(extraction.error)
                      : ai !== null
                        ? (ai.warnings[0] ?? '추출된 값을 확인하고 수정해 주세요.')
                        : '대표 이미지를 올린 뒤 분석을 요청하면 아래 항목이 자동으로 채워집니다.'}
                  </div>
                )}
              </div>
              <Button
                disabled={images.length === 0 || extraction.isPending}
                onClick={() => {
                  setAnalysisStartedAt(Date.now())
                  extraction.mutate(images.map((image) => image.file))
                }}
              >
                {extraction.isPending ? '분석 중…' : 'AI로 정보 채우기'}
              </Button>
            </div>
          </Panel>

          <ProductFields form={form} onChange={patch} ai={ai} />

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
