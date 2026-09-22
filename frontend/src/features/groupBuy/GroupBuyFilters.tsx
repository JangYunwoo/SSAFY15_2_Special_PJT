import { useEffect, useId, useState } from 'react'
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

/**
 * 목록 도구 (.list-tools).
 *
 * 검색과 정렬을 한 줄로 위에 두고, 카테고리 칩을 그 아래에 깝니다
 * (S15P21B205-227). 전에는 칩과 검색이 좌우로 나뉘어 있었는데, 카테고리가
 * 늘어나 칩이 두 줄이 되면 오른쪽 검색창이 아래로 밀려 내려가 어디 있는지
 * 찾아야 했습니다. 목록에서 가장 자주 쓰는 것이 검색이라 위로 올립니다.
 *
 * 정렬 <select> 에는 눈에 보이는 "정렬" 라벨을 붙입니다. 전에는 aria-label
 * 뿐이라 화면에서는 무엇을 기준으로 줄 세운 목록인지 알 수 없었습니다.
 */
export function GroupBuyFilters({ categoryId, sort, keyword, onChange }: GroupBuyFiltersProps) {
  const categories = useQuery(categoryQueries.tree())
  const sortId = useId()

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
    ...(categories.data ?? []).map((category) => ({ value: category.categoryId, label: category.name })),
  ]

  return (
    <div className="list-tools">
      <div className="tools-row">
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
        <div className="sort-field">
          <label className="sort-label" htmlFor={sortId}>
            정렬
          </label>
          <select
            id={sortId}
            className="input sort"
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

      <ChipFilter
        label="카테고리"
        options={chipOptions}
        value={categoryId}
        onChange={(next) => {
          onChange({ categoryId: next })
        }}
      />
    </div>
  )
}

