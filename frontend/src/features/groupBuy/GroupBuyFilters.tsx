import { useId } from 'react'

import { GROUP_BUY_SORT, GROUP_BUY_SORT_LABEL, type GroupBuySort } from '@/entities/groupBuy/model'

const SORT_OPTIONS = GROUP_BUY_SORT.map((value) => ({
  value,
  label: GROUP_BUY_SORT_LABEL[value],
}))

/**
 * 목록의 정렬 고르개.
 *
 * 제목 줄(.sec-head)의 오른쪽 끝에 들어갑니다. 아래에 따로 한 줄을 차지했을 때는
 * 제목과 카드 사이에 도구만 있는 띠가 하나 더 생겨, 무엇을 보는 목록인지보다
 * 도구가 먼저 눈에 들어왔습니다.
 *
 * 검색은 헤더의 전역 검색으로 옮겼습니다. 같은 화면에 검색창이 둘이면 어디에
 * 쳐야 할지 헷갈리고, 헤더로 검색해 들어왔을 때 이쪽 입력이 비어 있어 두 값이
 * 어긋나 보였습니다.
 *
 * 카테고리는 아이콘 타일(CategoryTiles)이 대신합니다. 홈에서 누른 카테고리가
 * 목록에서도 같은 자리, 같은 모양으로 눌린 채 보이도록 한 가지로 통일했습니다.
 *
 * 정렬 select 에는 눈에 보이는 "정렬" 라벨을 붙입니다. aria-label 뿐이면 화면에서
 * 무엇을 기준으로 줄 세운 목록인지 알 수 없습니다.
 */
export function GroupBuyFilters({
  sort,
  onChange,
}: {
  sort: GroupBuySort
  onChange: (next: { sort: GroupBuySort }) => void
}) {
  const sortId = useId()

  return (
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
  )
}


