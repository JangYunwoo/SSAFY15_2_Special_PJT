import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { categoryQueries } from '@/entities/category/queries'
import { GROUP_BUY_SORT, GROUP_BUY_SORT_LABEL, type GroupBuySort } from '@/entities/groupBuy/model'
import { useDebouncedValue } from '@/shared/lib/hooks/useDebouncedValue'
import { ChipFilter, type ChipOption } from '@/shared/ui/Chips'

import { ALL_CATEGORIES } from './useGroupBuyListParams'

interface GroupBuyFiltersProps {
  categoryId: string
  sort: GroupBuySort
  keyword: string
  onChange: (next: Partial<{ keyword: string; categoryId: string; sort: GroupBuySort }>) => void
}

const SORT_OPTIONS = GROUP_BUY_SORT.map((value) => ({
  value,
  label: GROUP_BUY_SORT_LABEL[value],
}))

/** 카테고리 칩 · 검색 · 정렬 (.list-tools) */
export function GroupBuyFilters({ categoryId, sort, keyword, onChange }: GroupBuyFiltersProps) {
  const categories = useQuery(categoryQueries.tree())

  // 입력은 즉시 반영하고, URL·요청은 타이핑이 멈춘 뒤에 갱신합니다.
  const [draft, setDraft] = useState(keyword)
  const debounced = useDebouncedValue(draft, 300)

  useEffect(() => {
    if (debounced !== keyword) onChange({ keyword: debounced })
    // onChange 는 매 렌더 새로 만들어질 수 있어 의존성에서 뺍니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced])

  const chipOptions: ChipOption<string>[] = [
    { value: ALL_CATEGORIES, label: '전체' },
    ...(categories.data ?? []).map((category) => ({ value: category.id, label: category.name })),
  ]

  return (
    <div className="list-tools">
      <ChipFilter
        label="카테고리"
        options={chipOptions}
        value={categoryId}
        onChange={(next) => {
          onChange({ categoryId: next })
        }}
      />

      <div className="tools-right">
        <input
          className="input search"
          type="search"
          placeholder="상품명 · 판매자 검색"
          aria-label="상품 검색"
          value={draft}
          onChange={(event) => {
            setDraft(event.target.value)
          }}
          onKeyDown={(event) => {
            // 폼 제출로 새로고침되지 않게 합니다.
            if (event.key === 'Enter') event.preventDefault()
          }}
        />
        <select
          className="input sort"
          aria-label="정렬 기준"
          value={sort}
          onChange={(event) => {
            onChange({ sort: event.target.value as GroupBuySort })
          }}
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
    </div>
  )
}
