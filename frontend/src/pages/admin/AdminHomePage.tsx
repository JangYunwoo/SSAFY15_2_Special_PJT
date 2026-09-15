import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'

import { adminApi } from '@/entities/admin/api'
import type { EscrowSummary } from '@/entities/admin/model'
import { adminQueries } from '@/entities/admin/queries'
import { queryKeys } from '@/shared/api/queryKeys'
import { toMessage } from '@/shared/api/errors'
import { number, won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { QueryBoundary } from '@/shared/ui/states'

/**
 * 운영 대시보드.
 *
 * 두 가지만 보여줍니다 — 플랫폼이 지금 들고 있는 돈(ESC-03)과, 사람이
 * 손대야 넘어가는 일의 건수입니다. 그래프는 두지 않았습니다. 백엔드에
 * 기간별 집계 API 가 없어 추세를 그리려면 목록을 전부 받아 프론트에서
 * 합쳐야 하고, 그렇게 만든 숫자는 화면마다 달라집니다.
 *
 * 대기 건수는 목록 API 를 `size=1` 로 불러 `meta.totalElements` 만 읽습니다.
 * 전용 집계 엔드포인트가 없어서인데, 본문을 한 건만 받으므로 비용은 거의 없습니다.
 */
export default function AdminHomePage() {
  useDocumentTitle('운영 대시보드')

  const escrow = useQuery(adminQueries.escrowSummary())

  return (
    <section className="section" style={{ paddingTop: 0 }}>
      <div className="sec-head">
        <div>
          <h2 className="sec-title">운영 대시보드</h2>
          <p className="sec-sub">
            플랫폼이 보관 중인 금액과 처리 대기 중인 건을 확인합니다.
          </p>
        </div>
      </div>

      <QueryBoundary query={escrow} loadingLabel="에스크로 집계를 불러오는 중입니다.">
        {(summary) => <EscrowTiles summary={summary} />}
      </QueryBoundary>

      <div className="split mt24">
        <div>
          <Panel title="처리 대기" description="사람이 판단해야 넘어가는 일">
            <WorkQueue />
          </Panel>

          <RecruitmentJobPanel />
        </div>

        <aside>
          <QueryBoundary query={escrow}>
            {(summary) => (
              <Panel title="에스크로 상세" description="상태별 보관 건수와 금액">
                <DataRow label="보관 중" hint="구매확정 전">
                  {number(summary.heldCount)}건 · {won(summary.heldAmount)}
                </DataRow>
                <DataRow label="정산 대기" hint="확정됐지만 아직 미지급">
                  {number(summary.awaitingSettlementCount)}건 ·{' '}
                  {won(summary.awaitingSettlementAmount)}
                </DataRow>
                <DataRow label="정산 완료" hint="판매자에게 지급">
                  {number(summary.settledCount)}건 · {won(summary.settledAmount)}
                </DataRow>
                <DataRow label="환불" hint="구매자에게 반환">
                  {number(summary.refundedCount)}건 · {won(summary.refundedAmount)}
                </DataRow>
                <DataRow label="전체">
                  {number(summary.totalCount)}건 · {won(summary.totalAmount)}
                </DataRow>

                <p className="small muted mt16" style={{ marginBottom: 0 }}>
                  통화 {summary.currency}. 보관 중과 정산 대기의 합이 플랫폼이 실제로 책임지는
                  금액입니다.
                </p>
              </Panel>
            )}
          </QueryBoundary>
        </aside>
      </div>
    </section>
  )
}

function EscrowTiles({ summary }: { summary: EscrowSummary }) {
  const liability = summary.heldAmount + summary.awaitingSettlementAmount

  return (
    <div className="kpis">
      <div className="lead">
        <div className="kpi-label">플랫폼 책임 금액</div>
        <div className="kpi-value">{won(liability)}</div>
        <div className="kpi-sub">보관 중 + 정산 대기</div>
      </div>
      <div>
        <div className="kpi-label">보관 중</div>
        <div className="kpi-value">{won(summary.heldAmount)}</div>
        <div className="kpi-sub">{number(summary.heldCount)}건 · 구매확정 전</div>
      </div>
      <div>
        <div className="kpi-label">정산 대기</div>
        <div className="kpi-value">{won(summary.awaitingSettlementAmount)}</div>
        <div className="kpi-sub">{number(summary.awaitingSettlementCount)}건 · 지급 차례</div>
      </div>
      <div>
        <div className="kpi-label">정산 완료</div>
        <div className="kpi-value">{won(summary.settledAmount)}</div>
        <div className="kpi-sub">{number(summary.settledCount)}건 누적</div>
      </div>
      <div>
        <div className="kpi-label">환불</div>
        <div className="kpi-value">{won(summary.refundedAmount)}</div>
        <div className="kpi-sub">{number(summary.refundedCount)}건 누적</div>
      </div>
    </div>
  )
}

/** 대기 건수 한 줄. 숫자를 못 받아도 줄 자체는 남겨 링크는 살려 둡니다. */
function QueueRow({
  label,
  hint,
  to,
  count,
  isPending,
  isError,
}: {
  label: string
  hint: string
  to: string
  count: number | undefined
  isPending: boolean
  isError: boolean
}) {
  let value = `${number(count ?? 0)}건`
  if (isPending) value = '…'
  else if (isError) value = '조회 실패'

  return (
    <DataRow
      label={
        <Link className="tlink" to={to}>
          {label}
        </Link>
      }
      hint={hint}
    >
      {value}
    </DataRow>
  )
}

function WorkQueue() {
  const settlements = useQuery(adminQueries.settlements({ status: 'PENDING', size: 1 }))
  const refunds = useQuery(adminQueries.refunds({ status: 'FAILED', size: 1 }))
  const returns = useQuery(adminQueries.returnReviews({ size: 1 }))
  const penalties = useQuery(adminQueries.penalties({ status: 'CANDIDATE', size: 1 }))
  const restrictions = useQuery(adminQueries.restrictionReviews())

  return (
    <>
      <QueueRow
        label="정산 실행 대기"
        hint="구매확정이 끝나 지급할 차례"
        to="/admin/settlements"
        count={settlements.data?.meta.totalElements}
        isPending={settlements.isPending}
        isError={settlements.isError}
      />
      <QueueRow
        label="환불 실패"
        hint="재처리하지 않으면 구매자 돈이 묶입니다"
        to="/admin/refunds"
        count={refunds.data?.meta.totalElements}
        isPending={refunds.isPending}
        isError={refunds.isError}
      />
      <QueueRow
        label="반품 검토"
        hint="구매확정 후 접수 · 이의제기"
        to="/admin/returns"
        count={returns.data?.meta.totalElements}
        isPending={returns.isPending}
        isError={returns.isError}
      />
      <QueueRow
        label="판매자 위반 후보"
        hint="확정하거나 기각해야 종료됩니다"
        to="/admin/penalties"
        count={penalties.data?.meta.totalElements}
        isPending={penalties.isPending}
        isError={penalties.isError}
      />
      <QueueRow
        label="청약철회 제한 심사"
        hint="승인하면 단순변심 반품이 막힙니다"
        to="/admin/products"
        count={restrictions.data?.length}
        isPending={restrictions.isPending}
        isError={restrictions.isError}
      />
    </>
  )
}

/**
 * 모집 마감 판정 배치 수동 실행.
 *
 * 평소에는 스케줄러가 돌립니다. 여기에 둔 것은 데모와 장애 복구용입니다 —
 * 실행하면 모집 성사·실패가 확정되고 실패 건은 환불이 시작되므로, 누르기 전에
 * 무슨 일이 일어나는지 적어둡니다.
 */
function RecruitmentJobPanel() {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)

  const run = useMutation({
    mutationFn: () => adminApi.runRecruitmentJob(),
    onSuccess: async () => {
      setConfirming(false)
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.all })
    },
  })

  return (
    <Panel
      className="mt16"
      title="모집 마감 판정"
      description="스케줄러가 도는 배치를 지금 한 번 돌립니다"
    >
      <div className="guide">
        <div className="guide-head">실행하면 일어나는 일</div>
        <ul>
          <li>마감 시각이 지난 공동구매의 성사·실패가 확정됩니다.</li>
          <li>목표 인원을 못 채운 건은 실패로 바뀌고 참여자 전원에게 환불이 시작됩니다.</li>
          <li>이미 판정된 건은 다시 건드리지 않습니다.</li>
        </ul>
      </div>

      {run.isError && (
        <p className="ai-hint fail mt16" role="alert">
          {toMessage(run.error)}
        </p>
      )}

      {/* isSuccess 로 좁혀지면 run.data 는 항상 있습니다. */}
      {run.isSuccess && (
        <div className="box rows mt16">
          <DataRow label="판정한 공동구매">{number(run.data.processedCount)}건</DataRow>
          <DataRow label="모집 성사">{number(run.data.fulfilledCount)}건</DataRow>
          <DataRow label="모집 실패">{number(run.data.failedCount)}건 · 환불 시작</DataRow>
        </div>
      )}

      {confirming ? (
        <div className="danger mt16">
          <div className="danger-head">되돌릴 수 없습니다</div>
          <p>
            실패로 판정된 공동구매는 되살릴 수 없고, 시작된 환불도 취소할 수 없습니다. 그래도
            실행하시겠습니까?
          </p>
          <div className="order-actions">
            <Button
              variant="ghost"
              onClick={() => {
                setConfirming(false)
              }}
            >
              취소
            </Button>
            <Button
              disabled={run.isPending}
              onClick={() => {
                run.mutate()
              }}
            >
              {run.isPending ? '실행 중…' : '실행 확정'}
            </Button>
          </div>
        </div>
      ) : (
        <div className="order-actions">
          <Button
            variant="ghost"
            onClick={() => {
              setConfirming(true)
            }}
          >
            배치 수동 실행
          </Button>
        </div>
      )}
    </Panel>
  )
}
