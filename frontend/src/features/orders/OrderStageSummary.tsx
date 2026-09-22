import type { ParticipationStatus } from '@/entities/model'
import type { ParticipationSummary } from '@/entities/participation/model'
import { won } from '@/shared/lib/format'

interface Stage {
  status: ParticipationStatus
  label: string
  hint: string
}

/**
 * 진행 중인 건이 지나는 네 단계.
 *
 * 끝난 건(CONFIRMED·SETTLED)과 취소·반품·환불 계열은 넣지 않습니다. 이 요약이
 * 답해야 하는 질문이 "지금 내 돈이 어디 있나" 라서, 이미 결론이 난 건을 세어봐야
 * 읽는 사람이 할 일이 없습니다. 그 건들은 아래 목록에서 그대로 볼 수 있습니다.
 */
const STAGES: readonly Stage[] = [
  { status: 'PAID', label: '성사 대기', hint: '모집 중 · 미달하면 환불' },
  { status: 'FULFILLED', label: '진행중', hint: '성사됨 · 발송 준비' },
  { status: 'SHIPPING', label: '배송중', hint: '받는 중' },
  { status: 'DELIVERED', label: '도착', hint: '구매확정이 필요합니다' },
]

/**
 * 거래관리 맨 위의 진행 단계 요약 (S15P21B205-229).
 *
 * 전에는 참여한 건이 카드로만 늘어서 있어, 내가 넣은 공동구매에 사람이 얼마나
 * 모였는지 알려면 건마다 펼쳐 보거나 상품 페이지로 직접 찾아가야 했습니다.
 * 에스크로로 돈을 맡겨 둔 서비스에서 가장 먼저 보여야 하는 것은 그 돈이 어느
 * 단계에 있는지입니다.
 *
 * 목록을 이미 받아와서 그리는 화면이라 집계는 화면에서 합니다. 요약만 따로
 * 내려주는 API 를 두면 목록과 요약이 서로 다른 시점의 값을 보여줄 수 있습니다.
 */
export function OrderStageSummary({ items }: { items: readonly ParticipationSummary[] }) {
  const rows = STAGES.map((stage) => {
    const matched = items.filter((item) => item.status === stage.status)
    return {
      ...stage,
      count: matched.length,
      amount: matched.reduce((total, item) => total + item.totalAmount, 0),
    }
  })

  // 진행 중인 건이 하나도 없으면 0 만 넉 줄 늘어놓게 됩니다. 그 자리는 목록의
  // 빈 상태 안내가 대신하는 편이 낫습니다.
  if (rows.every((row) => row.count === 0)) return null

  return (
    <div className="kpis mt24" aria-label="진행 단계 요약">
      {rows.map((row) => (
        <div key={row.status}>
          <div className="kpi-label">{row.label}</div>
          <div className="kpi-value">{row.count}건</div>
          <div className="kpi-sub">{row.count === 0 ? row.hint : won(row.amount)}</div>
        </div>
      ))}
    </div>
  )
}

