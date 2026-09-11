import { useEffect, useRef, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'

import { notificationApi } from '@/entities/notification/api'
import type { Notification } from '@/entities/notification/model'
import { notificationQueries } from '@/entities/notification/queries'
import { queryKeys } from '@/shared/api/queryKeys'
import { cx } from '@/shared/lib/cx'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

/** 목록에 한 번에 담을 개수. 더 보려면 알림 화면이 필요합니다. */
const PREVIEW_SIZE = 10

/** "3시간 전" 처럼 지난 시간을 표기합니다. */
function elapsed(createdAt: string, now: Date = new Date()): string {
  const ms = now.getTime() - new Date(createdAt).getTime()
  const minutes = Math.floor(ms / 60_000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${String(minutes)}분 전`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${String(hours)}시간 전`
  return `${String(Math.floor(hours / 24))}일 전`
}

/**
 * 헤더 알림. 안 읽은 개수를 뱃지로 보여주고, 누르면 목록을 펼칩니다.
 *
 * 목록은 펼칠 때만 불러옵니다(enabled). 안 읽은 개수만 주기적으로 갱신되므로
 * 닫혀 있는 동안 목록 요청이 반복되지 않습니다.
 */
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  const unread = useQuery(notificationQueries.unreadCount())
  const list = useQuery({ ...notificationQueries.list({ size: PREVIEW_SIZE }), enabled: open })

  // 바깥을 누르거나 Esc 를 누르면 닫습니다.
  useEffect(() => {
    if (!open) return

    const onPointerDown = (event: PointerEvent) => {
      if (event.target instanceof Node && rootRef.current?.contains(event.target) !== true) {
        setOpen(false)
      }
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }

    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const count = unread.data?.count ?? 0

  return (
    <div className="bell" ref={rootRef}>
      <button
        type="button"
        className="bell-btn"
        aria-expanded={open}
        aria-label={count > 0 ? `알림 ${String(count)}건` : '알림'}
        onClick={() => {
          setOpen((current) => !current)
        }}
      >
        알림
        {count > 0 && <span className="bell-badge">{count > 99 ? '99+' : count}</span>}
      </button>

      {open && (
        <div className="bell-panel" role="dialog" aria-label="알림 목록">
          <div className="bell-head">
            알림
            {count > 0 && <span className="small muted">안 읽음 {count}건</span>}
          </div>

          <QueryBoundary query={list} loadingLabel="알림을 불러오는 중입니다.">
            {(page) =>
              page.items.length === 0 ? (
                <EmptyState>받은 알림이 없습니다.</EmptyState>
              ) : (
                <ul className="bell-list">
                  {page.items.map((item) => (
                    <NotificationRow
                      key={item.id}
                      item={item}
                      onDone={() => {
                        setOpen(false)
                      }}
                    />
                  ))}
                </ul>
              )
            }
          </QueryBoundary>
        </div>
      )}
    </div>
  )
}

function NotificationRow({ item, onDone }: { item: Notification; onDone: () => void }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const markRead = useMutation({
    mutationFn: () => notificationApi.markRead(item.id),
    // 목록과 뱃지 숫자를 함께 되살립니다.
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
  })

  const open = () => {
    if (item.readAt === null) markRead.mutate()
    if (item.linkUrl !== null) {
      void navigate(item.linkUrl)
      onDone()
    }
  }

  return (
    <li>
      <button
        type="button"
        className={cx('bell-item', item.readAt === null && 'unread')}
        onClick={open}
        disabled={markRead.isPending}
      >
        <span className="bell-title">{item.title}</span>
        <span className="bell-body">{item.body}</span>
        <span className="bell-time">{elapsed(item.createdAt)}</span>
      </button>
    </li>
  )
}
