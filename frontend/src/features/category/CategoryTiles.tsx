import { Link, useSearchParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'

import { categoryQueries } from '@/entities/category/queries'
import { cx } from '@/shared/lib/cx'

import { CategoryIcon } from './CategoryIcon'

/** 전체(카테고리 해제). useGroupBuyListParams 의 ALL_CATEGORIES 와 같은 값입니다 */
const ALL = 'all'

/**
 * 카테고리 아이콘 줄.
 *
 * 홈에서는 입구로, 목록에서는 필터로 씁니다. 같은 줄을 두 곳에서 쓰는 이유는
 * 홈에서 누른 카테고리가 목록에서도 같은 자리, 같은 모양으로 보여야 어디를
 * 보고 있는지 이어지기 때문입니다.
 *
 * `activeId` 를 주면 필터로 동작해 선택된 타일이 눌린 모양이 됩니다. 주지
 * 않으면(홈) 선택 표시 없이 입구 목록으로만 그립니다.
 *
 * '전체' 타일은 두 화면 모두에 둡니다. 목록에서는 카테고리 선택을 푸는
 * 버튼이고 홈에서는 전체 목록으로 가는 입구인데, 한쪽에만 두면 뒤따르는
 * 카테고리가 한 칸씩 밀려 같은 카테고리가 두 화면에서 다른 자리에 옵니다.
 * 화면을 옮길 때마다 눈으로 다시 찾아야 합니다.
 *
 * 카테고리를 못 불러오면 줄 전체를 그리지 않습니다. 빈 원이 스물두 개 늘어서는
 * 것보다 자리가 없는 편이 낫습니다.
 */
export function CategoryTiles({
  activeId,
  className,
}: {
  activeId?: string | undefined
  className?: string | undefined
}) {
  const categories = useQuery(categoryQueries.tree())
  const [params] = useSearchParams()
  const items = categories.data ?? []

  if (items.length === 0) return null

  const isFilter = activeId !== undefined

  /*
   * 카테고리만 바꾸고 나머지 조건은 그대로 둡니다.
   *
   * 디지털기기를 인기순으로 보다가 농산물로 옮기는 것은 옆으로 한 칸 가는
   * 동작입니다. 링크를 /group-buys?categoryId=... 로 고정하면 정렬이 기본값으로
   * 돌아가, 보던 방식을 매번 다시 골라야 했습니다.
   *
   * 쪽 번호는 버립니다. 카테고리가 바뀌면 결과 수가 달라져 3쪽이 비어 있을 수
   * 있습니다. 홈에서는 이어갈 조건이 없으므로 그냥 목록 주소만 만듭니다.
   */
  const hrefFor = (categoryId: string | null) => {
    if (!isFilter) {
      return categoryId === null ? '/group-buys' : `/group-buys?categoryId=${categoryId}`
    }
    const next = new URLSearchParams(params)
    if (categoryId === null) next.delete('categoryId')
    else next.set('categoryId', categoryId)
    next.delete('page')
    const query = next.toString()
    return query === '' ? '/group-buys' : `/group-buys?${query}`
  }

  return (
    <nav className={cx('cat-tiles', className)} aria-label="카테고리">
      <Link className={cx(isFilter && activeId === ALL && 'is-on')} to={hrefFor(null)}>
        <span className="cat-tile-icon">
          <CategoryIcon name="전체" />
        </span>
        <span className="cat-tile-label">전체</span>
      </Link>
      {items.map((category) => (
        <Link
          key={category.categoryId}
          className={cx(activeId === category.categoryId && 'is-on')}
          to={hrefFor(category.categoryId)}
          aria-current={activeId === category.categoryId ? 'page' : undefined}
        >
          <span className="cat-tile-icon">
            <CategoryIcon name={category.name} />
          </span>
          <span className="cat-tile-label">{category.name}</span>
        </Link>
      ))}
    </nav>
  )
}


