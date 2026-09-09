import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import { productApi } from '@/entities/product/api'
import type { Product } from '@/entities/product/model'
import { productQueries } from '@/entities/product/queries'
import { ImageUploader, type PickedImage } from '@/features/product/ImageUploader'
import { ProductFields } from '@/features/product/ProductFields'
import {
  isProductFormReady,
  type ProductFormState,
} from '@/features/product/productForm'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import type { SubmitHandlerEvent } from '@/shared/lib/dom'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { QueryBoundary } from '@/shared/ui/states'

/** 서버에서 받은 상품을 입력 폼 형태로 옮깁니다. */
function toFormState(product: Product): ProductFormState {
  return {
    name: product.name,
    brand: product.brand ?? '',
    modelName: product.modelName ?? '',
    color: product.color ?? '',
    categoryId: product.categoryId,
    price: String(product.price),
    description: product.description,
    restricted: product.withdrawalRestrictionReasonId === null ? 'NONE' : 'RESTRICTED',
    reasonId: product.withdrawalRestrictionReasonId ?? '',
  }
}

export default function SellerProductEditPage() {
  useDocumentTitle('상품 수정')

  const { productId = '' } = useParams()
  const query = useQuery(productQueries.detail(productId))

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">상품 수정</h2>
          <p className="sec-sub">공동구매가 연결되기 전까지만 수정·삭제할 수 있습니다.</p>
        </div>
      </div>

      <QueryBoundary query={query}>
        {(product) => <EditForm key={product.id} product={product} />}
      </QueryBoundary>
    </section>
  )
}

function EditForm({ product }: { product: Product }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [form, setForm] = useState<ProductFormState>(() => toFormState(product))
  // 비워두면 기존 이미지를 그대로 둡니다. 새로 고르면 전부 교체됩니다.
  const [images, setImages] = useState<PickedImage[]>([])

  const patch = (next: Partial<ProductFormState>) => {
    setForm((current) => ({ ...current, ...next }))
  }

  const refreshList = () => queryClient.invalidateQueries({ queryKey: queryKeys.sellerProducts.all })

  const update = useMutation({
    mutationFn: () =>
      productApi.update(
        product.id,
        {
          name: form.name,
          brand: form.brand,
          modelName: form.modelName,
          color: form.color,
          categoryId: form.categoryId,
          price: Number(form.price),
          description: form.description,
          thumbnailIndex: 0,
          aiExtracted: product.aiExtracted,
          withdrawalRestrictionReasonId:
            form.restricted === 'RESTRICTED' ? form.reasonId || null : null,
        },
        images.map((image) => image.file),
      ),
    onSuccess: async () => {
      await refreshList()
      await navigate('/seller/products')
    },
  })

  const remove = useMutation({
    mutationFn: () => productApi.remove(product.id),
    onSuccess: async () => {
      await refreshList()
      await navigate('/seller/products')
    },
  })

  const locked = product.linkedGroupBuyId !== null
  const busy = update.isPending || remove.isPending
  const error = update.error ?? remove.error

  const submit = (event: SubmitHandlerEvent) => {
    event.preventDefault()
    update.mutate()
  }

  const confirmRemove = () => {
    // 되돌릴 수 없는 동작이라 한 번 더 묻습니다.
    if (window.confirm(`'${product.name}' 상품을 삭제할까요? 되돌릴 수 없습니다.`)) {
      remove.mutate()
    }
  }

  return (
    <form className="split" onSubmit={submit}>
      <div>
        {locked && (
          <div className="guide" role="status">
            <div className="guide-head">수정할 수 없는 상품입니다</div>
            <p className="small" style={{ margin: 0 }}>
              진행 중인 공동구매가 연결되어 있어 내용을 바꾸거나 삭제할 수 없습니다.
            </p>
          </div>
        )}

        <Panel
          className={locked ? 'mt16' : ''}
          title="상품 이미지"
          description="새로 올리면 기존 이미지를 대체합니다 · 그대로 두려면 비워 두세요"
        >
          <ImageUploader images={images} onChange={setImages} />
          {images.length === 0 && (
            <p className="small muted mt16" style={{ marginBottom: 0 }}>
              현재 등록된 이미지 {product.images.length}장을 유지합니다.
            </p>
          )}
        </Panel>

        <ProductFields form={form} onChange={patch} />

        {error !== null && (
          <p className="ai-hint mt16" role="alert">
            {toMessage(error)}
          </p>
        )}

        <Button
          type="submit"
          block
          className="mt24"
          disabled={locked || busy || !isProductFormReady(form)}
        >
          {update.isPending ? '저장 중…' : '수정 내용 저장'}
        </Button>

        <Button
          variant="ghost"
          block
          className="mt8"
          disabled={locked || busy}
          onClick={confirmRemove}
        >
          {remove.isPending ? '삭제 중…' : '상품 삭제'}
        </Button>
      </div>

      <aside className="sticky">
        <Panel title="상품 정보" description="등록된 내용">
          <DataRow label="상품 번호">{product.id}</DataRow>
          <DataRow label="카테고리">{product.categoryName}</DataRow>
          <DataRow label="등록 이미지">{product.images.length}장</DataRow>
          <DataRow label="AI 등록">{product.aiExtracted ? '예' : '아니오'}</DataRow>
          <DataRow label="연결된 공동구매">
            {product.linkedGroupBuyId === null ? '없음' : `#${product.linkedGroupBuyId}`}
          </DataRow>

          <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
            공동구매가 한 번이라도 연결되면 가격과 설명을 바꿀 수 없습니다. 모집이 끝난 뒤에는 같은
            상품으로 새 공동구매를 열 수 있습니다.
          </p>
        </Panel>
      </aside>
    </form>
  )
}
