import type { PageMeta } from '@/shared/api/types'
import { cx } from '@/shared/lib/cx'

/** 한 번에 노출할 페이지 번호 개수 */
const WINDOW = 5

/**
 * 현재 페이지를 가운데 두고 최대 WINDOW 개의 번호를 만듭니다.
 * 앞뒤가 모자라면 반대쪽으로 밀어 항상 같은 개수를 유지합니다.
 * (1페이지에서 `1 2 3 4 5`, 마지막에서 `6 7 8 9 10`)
 */
function pageNumbers(page: number, totalPages: number): number[] {
  const count = Math.min(WINDOW, totalPages)
  const centered = page - Math.floor(count / 2)
  const start = Math.max(0, Math.min(centered, totalPages - count))
  return Array.from({ length: count }, (_, index) => start + index)
}

interface PaginationProps {
  meta: PageMeta
  onChange: (page: number) => void
  /** 한 화면에 목록이 둘 이상일 때 구분하는 이름 (스크린리더용) */
  label?: string | undefined
}

/**
 * 목록 하단 페이지 이동. `meta.page` 는 0부터 세지만 화면에는 1부터 보여줍니다.
 * 페이지가 하나뿐이면 넘길 곳이 없으므로 아무것도 그리지 않습니다.
 */
export function Pagination({ meta, onChange, label = '페이지' }: PaginationProps) {
  const { page, totalPages } = meta
  if (totalPages <= 1) return null

  const isFirst = page <= 0
  const isLast = page >= totalPages - 1

  return (
    <nav className="pager" aria-label={label}>
      <button
        type="button"
        className="pager-btn"
        onClick={() => {
          onChange(page - 1)
        }}
        disabled={isFirst}
      >
        이전
      </button>

      {pageNumbers(page, totalPages).map((index) => {
        const isCurrent = index === page
        return (
          <button
            key={index}
            type="button"
            className={cx('pager-btn', isCurrent && 'on')}
            {...(isCurrent ? { 'aria-current': 'page' as const } : {})}
            onClick={() => {
              onChange(index)
            }}
          >
            {index + 1}
          </button>
        )
      })}

      <button
        type="button"
        className="pager-btn"
        onClick={() => {
          onChange(page + 1)
        }}
        disabled={isLast}
      >
        다음
      </button>
    </nav>
  )
}
