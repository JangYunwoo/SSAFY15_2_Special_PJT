import type { ReactNode } from 'react'

import { toMessage } from '@/shared/api/errors'
import { cx } from '@/shared/lib/cx'

import { Button, ButtonLink } from './Button'

export interface EmptyStateAction {
  to: string
  label: string
}

/**
 * 비어 있는 목록 자리 (.empty).
 *
 * 처음 온 사람에게는 이 화면이 서비스의 첫인상입니다 (S15P21B205-230).
 * "참여한 공동구매가 아직 없습니다" 한 줄만 두면 맞는 말이지만 다음에 뭘 해야
 * 하는지가 없어, 막 가입한 사람이 여기서 멈춥니다. 그래서 제목 · 설명 · 다음
 * 행동 세 가지를 한 컴포넌트에서 받습니다.
 *
 * 셋 다 선택입니다. 관리자·판매자의 운영 화면처럼 "없음"이 정상이고 권할 행동도
 * 없는 자리에서는 예전처럼 문장 하나만 넘기면 됩니다. 빈 목록마다 버튼을 다는
 * 것이 목적이 아니라, 달아야 할 곳에서 같은 모양으로 달리게 하는 것이 목적입니다.
 */
export function EmptyState({
  children,
  className,
  title,
  action,
}: {
  children?: ReactNode
  /** 앞 요소와 붙어 보일 때 여백을 주기 위해 (예: mt24) */
  className?: string | undefined
  title?: string | undefined
  action?: EmptyStateAction | undefined
}) {
  return (
    <div className={cx('empty', className)}>
      {title !== undefined && <p className="empty-title">{title}</p>}
      {children !== undefined && <p className="empty-desc">{children}</p>}
      {action !== undefined && (
        <ButtonLink className="mt16" to={action.to}>
          {action.label}
        </ButtonLink>
      )}
    </div>
  )
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

/**
 * 백엔드 구현을 기다리는 화면.
 *
 * 목 서버를 걷어낸 뒤로 화면에 뜨는 값은 전부 실제 응답입니다. 아직 API 가
 * 없는 기능까지 그럴듯하게 채우면 되는 것과 안 되는 것을 구분할 수 없어,
 * 준비 중임을 그대로 밝힙니다.
 */
export function PendingBackendState({ children }: { children?: ReactNode }) {
  return (
    <div className="empty">
      <p style={{ margin: 0 }}>백엔드 API가 아직 준비되지 않은 기능입니다.</p>
      {children && (
        <p className="small muted mt16" style={{ marginBottom: 0 }}>
          {children}
        </p>
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
