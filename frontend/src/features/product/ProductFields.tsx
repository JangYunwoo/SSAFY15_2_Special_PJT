import { useQuery } from '@tanstack/react-query'

import { categoryQueries } from '@/entities/category/queries'
import { productQueries } from '@/entities/product/queries'
import type { ProductFormState } from '@/features/product/productForm'
import { aiHintText, type AiExtractionResult } from '@/features/product/useAiExtraction'
import { number } from '@/shared/lib/format'
import { FieldRow, SelectField, TextAreaField, TextField } from '@/shared/ui/Field'
import { Panel } from '@/shared/ui/Panel'
import { RadioGroup } from '@/shared/ui/RadioGroup'

interface ProductFieldsProps {
  form: ProductFormState
  onChange: (next: Partial<ProductFormState>) => void
  /** 등록 화면에서만 넘깁니다. 수정 화면에는 AI 추출이 없습니다. */
  ai?: AiExtractionResult | null | undefined
}

/**
 * 상품 정보 · 청약철회 제한 입력.
 * 등록(SellerProductNewPage)과 수정(SellerProductEditPage)이 같은 폼을 씁니다.
 */
export function ProductFields({ form, onChange, ai = null }: ProductFieldsProps) {
  const categories = useQuery(categoryQueries.tree())
  const withdrawalReasons = useQuery(productQueries.withdrawalReasons())
  const selectedReason = withdrawalReasons.data?.find((reason) => reason.id === form.reasonId)

  return (
    <>
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
          onChange={(event) => {
            onChange({ name: event.target.value })
          }}
          hint={aiHintText(ai?.hints.name)}
        />

        <FieldRow className="mt8">
          <TextField
            label="브랜드"
            placeholder="예) Apple"
            value={form.brand}
            onChange={(event) => {
              onChange({ brand: event.target.value })
            }}
            hint={aiHintText(ai?.hints.brand)}
          />
          {/*
            모델명은 선택 항목입니다(백엔드도 @Size 만 걸려 있고 컬럼이 nullable).
            그런데 예시가 노트북 품번 하나뿐이라 전자제품이 아니면 무엇을 적어야
            할지 알기 어려웠습니다. 식품·생활용품까지 아우르도록 예시를 넓히고,
            비워도 된다는 점을 밝힙니다.
          */}
          <TextField
            label="모델명 · 규격"
            placeholder="예) MGN63KH/A · 500ml · 2kg"
            value={form.modelName}
            onChange={(event) => {
              onChange({ modelName: event.target.value })
            }}
            hint={aiHintText(ai?.hints.modelName) ?? '품번이 없으면 용량·규격을 적거나 비워 둡니다.'}
          />
        </FieldRow>

        <FieldRow className="mt8">
          <SelectField
            label="카테고리"
            required
            placeholder="선택"
            value={form.categoryId}
            onChange={(event) => {
              onChange({ categoryId: event.target.value })
            }}
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
            onChange={(event) => {
              onChange({ color: event.target.value })
            }}
            hint={aiHintText(ai?.hints.color)}
          />
        </FieldRow>

        <TextField
          label="상품 가격"
          required
          inputMode="numeric"
          placeholder="공동구매 1회분 총액"
          value={form.price === '' ? '' : number(Number(form.price))}
          onChange={(event) => {
            onChange({ price: event.target.value.replace(/\D/g, '') })
          }}
          hint={aiHintText(ai?.hints.price) ?? '공동구매 1회분 총액을 입력합니다.'}
        />

        <TextAreaField
          label="상품 설명"
          required
          placeholder="상품 구성, 보증, 배송 조건 등을 입력해 주세요."
          value={form.description}
          onChange={(event) => {
            onChange({ description: event.target.value })
          }}
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
          value={form.restricted}
          onChange={(restricted) => {
            onChange(
              restricted === 'NONE'
                ? { restricted, reasonId: '', evidenceUrl: '' }
                : { restricted },
            )
          }}
          options={[
            { value: 'NONE', label: '제한 없음' },
            { value: 'RESTRICTED', label: '제한 상품' },
          ]}
        />

        <SelectField
          label="제한 사유"
          placeholder="사유 선택"
          disabled={form.restricted === 'NONE'}
          value={form.reasonId}
          onChange={(event) => {
            onChange({ reasonId: event.target.value })
          }}
          options={(withdrawalReasons.data ?? []).map((reason) => ({
            value: reason.id,
            label: reason.description,
          }))}
        />

        <TextField
          label="증빙 URL"
          required={form.restricted === 'RESTRICTED' && selectedReason?.requiresEvidence === true}
          disabled={form.restricted === 'NONE'}
          placeholder="https://..."
          value={form.evidenceUrl}
          onChange={(event) => {
            onChange({ evidenceUrl: event.target.value })
          }}
          hint={
            selectedReason?.requiresEvidence === true
              ? '선택한 사유는 증빙 URL이 필수입니다.'
              : undefined
          }
        />

        <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
          제한 상품으로 지정하면 관리자 검토를 거친 뒤 공동구매 상세 화면에 사유가 함께 표시됩니다.
          제한 상품이라도 상품 하자·오배송·표시광고 불일치의 경우에는 반품이 가능합니다.
        </p>
      </Panel>
    </>
  )
}
