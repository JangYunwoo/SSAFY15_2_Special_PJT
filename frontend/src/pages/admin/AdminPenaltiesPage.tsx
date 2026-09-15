import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { adminApi } from '@/entities/admin/api'
import {
  penaltyReasonLabel,
  penaltyStatusDisplay,
  type PenaltyStatus,
} from '@/entities/admin/model'
import { adminQueries } from '@/entities/admin/queries'
import type { Penalty } from '@/entities/settlement/model'
import { toMessage } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { date, dateTime, groupBuyNo, number } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { usePageParam } from '@/shared/lib/hooks/usePageParam'
import { Button } from '@/shared/ui/Button'
import { ChipFilter } from '@/shared/ui/Chips'
import { DataRow } from '@/shared/ui/DataRow'
import { TextAreaField } from '@/shared/ui/Field'
import { Pagination } from '@/shared/ui/Pagination'
import { Panel } from '@/shared/ui/Panel'
import { StateChip } from '@/shared/ui/StateChip'
import { EmptyState, QueryBoundary } from '@/shared/ui/states'

type Filter = 'ALL' | PenaltyStatus

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'CANDIDATE', label: '검토 대기' },
  { value: 'CONFIRMED', label: '확정' },
  { value: 'DISMISSED', label: '기각' },
  { value: 'REVOKED', label: '확정 취소' },
]

type Action = 'confirm' | 'dismiss' | 'revoke'

const ACTION_LABEL: Record<Action, string> = {
  confirm: '위반 확정',
  dismiss: '기각',
  revoke: '확정 취소',
}

/**
 * PEN-02~06 판매자 제재.
 *
 * 시스템이 자동으로 올린 "후보" 를 사람이 확정하거나 기각합니다. 확정은
 * 누적으로 쌓이고 한도에 닿으면 판매자가 정지되므로, 어느 쪽이든 판단 근거를
 * 필수로 받습니다(백엔드에서도 `memo` 가 `@NotBlank`).
 */
export default function AdminPenaltiesPage() {
  useDocumentTitle('판매자 제재')

  const { page, goToPage } = usePageParam()
  const [filter, setFilter] = useState<Filter>('CANDIDATE')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const query = useQuery(
    adminQueries.penalties({ page, size: 20, ...(filter === 'ALL' ? {} : { status: filter }) }),
  )

  const changeFilter = (next: Filter) => {
    setFilter(next)
    setSelectedId(null)
    goToPage(0)
  }

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">판매자 제재</h2>
          <p className="sec-sub">
            자동 감지된 위반 후보를 검토합니다. 확정은 누적되고, 한도에 닿으면 판매자가
            정지됩니다.
          </p>
        </div>
      </div>

      <ChipFilter label="제재 상태" value={filter} onChange={changeFilter} options={FILTERS} />

      <div className="split">
        <div>
          <QueryBoundary query={query} loadingLabel="위반 목록을 불러오는 중입니다.">
            {(result) =>
              result.items.length === 0 ? (
                <EmptyState>해당 상태의 위반 건이 없습니다.</EmptyState>
              ) : (
                <>
                  <div className="table-scroll">
                    <table className="list">
                      <thead>
                        <tr>
                          <th>위반 번호</th>
                          <th>판매자</th>
                          <th>유형</th>
                          <th>공동구매</th>
                          <th>상태</th>
                          <th>감지</th>
                        </tr>
                      </thead>
                      <tbody>
                        {result.items.map((item) => (
                          <PenaltyRow
                            key={item.penaltyId}
                            item={item}
                            selected={item.penaltyId === selectedId}
                            onSelect={() => {
                              setSelectedId(item.penaltyId)
                            }}
                          />
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Pagination meta={result.meta} onChange={goToPage} label="위반 목록 페이지" />
                </>
              )
            }
          </QueryBoundary>
        </div>

        <aside>
          {selectedId === null ? (
            <Panel title="위반 상세" description="목록에서 한 건을 고르면 검토할 수 있습니다">
              <EmptyState>선택된 위반 건이 없습니다.</EmptyState>
            </Panel>
          ) : (
            <PenaltyDetailPanel penaltyId={selectedId} />
          )}
        </aside>
      </div>
    </section>
  )
}

function PenaltyRow({
  item,
  selected,
  onSelect,
}: {
  item: Penalty
  selected: boolean
  onSelect: () => void
}) {
  const status = penaltyStatusDisplay(item.status)

  return (
    <tr style={selected ? { background: 'var(--soft)' } : undefined}>
      <td>
        <button type="button" className="tlink" onClick={onSelect}>
          <b className="mono">#{item.penaltyId}</b>
        </button>
      </td>
      <td>
        <span className="mono">#{item.sellerId}</span>
      </td>
      <td>{penaltyReasonLabel(item.reasonType)}</td>
      <td>{item.groupBuyId === null ? '-' : groupBuyNo(item.groupBuyId)}</td>
      <td>
        <StateChip tone={status.tone}>{status.label}</StateChip>
      </td>
      <td>{date(item.detectedAt)}</td>
    </tr>
  )
}

function PenaltyDetailPanel({ penaltyId }: { penaltyId: string }) {
  const queryClient = useQueryClient()
  const query = useQuery(adminQueries.penalty(penaltyId))

  const [action, setAction] = useState<Action | null>(null)
  const [memo, setMemo] = useState('')

  const review = useMutation({
    mutationFn: (next: Action) => {
      if (next === 'confirm') return adminApi.confirmPenalty(penaltyId, memo.trim())
      if (next === 'dismiss') return adminApi.dismissPenalty(penaltyId, memo.trim())
      return adminApi.revokePenalty(penaltyId, memo.trim())
    },
    onSuccess: async () => {
      setAction(null)
      setMemo('')
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.all })
    },
  })

  return (
    <QueryBoundary query={query} loadingLabel="위반 상세를 불러오는 중입니다.">
      {({ penalty, confirmedHistory, penaltyLimit }) => {
        const status = penaltyStatusDisplay(penalty.status)
        const isCandidate = penalty.status === 'CANDIDATE'
        const isConfirmed = penalty.status === 'CONFIRMED'
        // 이 건을 확정하면 누적이 몇 회가 되는지. 정지 여부를 가르는 숫자입니다.
        const afterConfirm = confirmedHistory.length + (isConfirmed ? 0 : 1)
        const willSuspend = afterConfirm >= penaltyLimit

        return (
          <Panel
            title={penaltyReasonLabel(penalty.reasonType)}
            description={`위반 #${penalty.penaltyId} · 판매자 #${penalty.sellerId}`}
            aside={
              <div className="order-amount">
                {number(confirmedHistory.length)}/{number(penaltyLimit)}
                <span className="small muted">확정 누적</span>
              </div>
            }
          >
            <div className="box rows">
              <DataRow label="상태">
                <StateChip tone={status.tone}>{status.label}</StateChip>
              </DataRow>
              <DataRow label="공동구매">
                {penalty.groupBuyId === null ? '-' : groupBuyNo(penalty.groupBuyId)}
              </DataRow>
              <DataRow label="감지 시각">{dateTime(penalty.detectedAt)}</DataRow>
              <DataRow label="검토 시각">{dateTime(penalty.reviewedAt)}</DataRow>
              <DataRow label="검토자">
                {penalty.reviewedBy === null ? '-' : `관리자 #${penalty.reviewedBy}`}
              </DataRow>
            </div>

            <DataRow className="mt16" label="감지 근거">
              {penalty.detail ?? '기록된 근거가 없습니다.'}
            </DataRow>
            {penalty.memo !== null && <DataRow label="검토 메모">{penalty.memo}</DataRow>}

            <div className="guide mt16">
              <div className="guide-head">이 판매자의 확정 이력</div>
              {confirmedHistory.length === 0 ? (
                <p className="small" style={{ margin: 0 }}>
                  확정된 위반이 없습니다. 이번이 첫 건입니다.
                </p>
              ) : (
                <ul>
                  {confirmedHistory.map((item) => (
                    <li key={item.penaltyId}>
                      {date(item.reviewedAt ?? item.detectedAt)} ·{' '}
                      <b>{penaltyReasonLabel(item.reasonType)}</b>
                      {item.memo !== null && ` — ${item.memo}`}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {review.isError && (
              <p className="ai-hint fail mt16" role="alert">
                {toMessage(review.error)}
              </p>
            )}

            {action !== null ? (
              <div className="mt16">
                {action === 'confirm' && willSuspend && (
                  <div className="danger">
                    <div className="danger-head">확정하면 판매가 정지됩니다</div>
                    <p>
                      이 건을 확정하면 누적 {number(afterConfirm)}회로 한도({number(penaltyLimit)}
                      회)에 닿아 판매자 #{penalty.sellerId} 의 신규 상품·공동구매 등록이
                      막힙니다. 진행 중인 공동구매는 그대로 유지됩니다.
                    </p>
                  </div>
                )}

                <TextAreaField
                  label={`${ACTION_LABEL[action]} 근거`}
                  required
                  rows={3}
                  maxLength={500}
                  placeholder="왜 그렇게 판단했는지 적어주세요. 판매자가 이의를 제기할 때 이 내용이 근거가 됩니다."
                  hint="최대 500자 · 판매자에게도 그대로 보입니다"
                  value={memo}
                  onChange={(event) => {
                    setMemo(event.target.value)
                  }}
                />
                <div className="order-actions">
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setAction(null)
                    }}
                  >
                    취소
                  </Button>
                  <Button
                    disabled={memo.trim() === '' || review.isPending}
                    onClick={() => {
                      review.mutate(action)
                    }}
                  >
                    {review.isPending ? '처리 중…' : `${ACTION_LABEL[action]} 확정`}
                  </Button>
                </div>
              </div>
            ) : (
              <div className="order-actions">
                {isCandidate && (
                  <>
                    <Button
                      onClick={() => {
                        setAction('confirm')
                      }}
                    >
                      위반 확정
                    </Button>
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setAction('dismiss')
                      }}
                    >
                      기각
                    </Button>
                  </>
                )}
                {isConfirmed && (
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setAction('revoke')
                    }}
                  >
                    확정 취소
                  </Button>
                )}
                {!isCandidate && !isConfirmed && (
                  <span className="small muted">검토가 끝난 건입니다.</span>
                )}
              </div>
            )}

            {isConfirmed && (
              <p className="small muted mt16" style={{ marginBottom: 0 }}>
                확정을 취소하면 누적에서 빠집니다. 누적이 한도 아래로 내려가면 정지도 함께
                풀립니다.
              </p>
            )}
          </Panel>
        )
      }}
    </QueryBoundary>
  )
}
