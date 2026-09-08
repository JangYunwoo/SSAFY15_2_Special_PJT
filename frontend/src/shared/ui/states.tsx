import type { ReactNode } from 'react'

import { toMessage } from '@/shared/api/errors'

import { Button } from './Button'

/** 검색·필터 결과가 없을 때 (.empty) */
export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}

/** 데이터를 불러오는 중 */
export function LoadingState({ label = '불러오는 중입니다.' }: { label?: string | undefined }) {
  return (
    <div className="empty" role="status" aria-live="polite">
      {label}
    </div>
  )
}

/** 조회 실패. 재시도 버튼을 함께 노출합니다. */
export function ErrorState({
  error,
  onRetry,
}: {
  error: unknown
  onRetry?: (() => void) | undefined
}) {
  return (
    <div className="empty" role="alert">
      <p style={{ margin: 0 }}>{toMessage(error)}</p>
      {onRetry && (
        <Button variant="ghost" className="mt16" onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </div>
  )
}

interface QueryBoundaryProps<TData> {
  /** TanStack Query 결과를 그대로 넘깁니다. */
  query: {
    data: TData | undefined
    isPending: boolean
    isError: boolean
    error: unknown
    refetch: () => void
  }
  loadingLabel?: string | undefined
  children: (data: TData) => ReactNode
}

/**
 * 로딩 · 오류 · 성공 세 갈래를 한곳에서 처리합니다.
 * 페이지마다 같은 분기를 반복해서 쓰지 않도록 만든 얇은 래퍼입니다.
 */
export function QueryBoundary<TData>({ query, loadingLabel, children }: QueryBoundaryProps<TData>) {
  if (query.isPending) return <LoadingState {...(loadingLabel ? { label: loadingLabel } : {})} />
  if (query.isError) return <ErrorState error={query.error} onRetry={query.refetch} />
  if (query.data === undefined) return <EmptyState>표시할 내용이 없습니다.</EmptyState>
  return <>{children(query.data)}</>
}
