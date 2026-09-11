import { useEffect } from 'react'

import { useQuery } from '@tanstack/react-query'

import { CRYPTO_PHASE_LABEL } from '@/entities/payment/model'
import { paymentQueries } from '@/entities/payment/queries'
import { toMessage } from '@/shared/api/errors'
import { dateTime, number } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'
import { DataRow } from '@/shared/ui/DataRow'
import { Panel } from '@/shared/ui/Panel'
import { ProgressBar } from '@/shared/ui/ProgressBar'
import { StateChip, type StateTone } from '@/shared/ui/StateChip'
import { LoadingState } from '@/shared/ui/states'

const PHASE_TONE: Record<string, StateTone> = {
  AWAITING_TX: 'neutral',
  AWAITING_RECEIPT: 'live',
  CONFIRMING: 'ship',
  COMPLETED: 'ship',
  FAILED: 'alert',
}

interface CryptoWaitingProps {
  paymentId: string
  /** 컨펌이 충족돼 결제가 확정되면 부릅니다. */
  onCompleted: () => void
  /** 실패했을 때 입력 화면으로 되돌립니다. */
  onRetry: () => void
}

/**
 * CRYP-04 입금·컨펌 진행 상태.
 *
 * 해시를 등록해도 결제 완료가 아닙니다. 필요 컨펌 수에 도달할 때까지
 * 서버에 물어보고(5초 주기), COMPLETED 나 FAILED 에서 멈춥니다.
 */
export function CryptoWaiting({ paymentId, onCompleted, onRetry }: CryptoWaitingProps) {
  const status = useQuery(paymentQueries.cryptoStatus(paymentId))
  const phase = status.data?.phase

  useEffect(() => {
    if (phase === 'COMPLETED') onCompleted()
  }, [phase, onCompleted])

  if (status.isPending) return <LoadingState label="입금 상태를 확인하고 있습니다." />
  if (status.isError) {
    return (
      <Panel title="입금 확인" description="상태를 불러오지 못했습니다">
        <p className="ai-hint" role="alert">
          {toMessage(status.error)}
        </p>
      </Panel>
    )
  }

  const {
    phase: currentPhase,
    confirmations,
    requiredConfirmations,
    txHash,
    blockNumber,
    receiptStatus,
    assetAmount,
    confirmedAt,
    failReason,
  } = status.data
  const failed = currentPhase === 'FAILED'

  return (
    <Panel
      title="입금 확인"
      description={failed ? '결제를 완료하지 못했습니다' : '송금이 확인되면 자동으로 완료됩니다'}
    >
      <div className="mb16">
        <StateChip tone={PHASE_TONE[currentPhase] ?? 'neutral'}>
          {CRYPTO_PHASE_LABEL[currentPhase]}
        </StateChip>
      </div>

      {!failed && (
        <>
          <ProgressBar current={confirmations} target={requiredConfirmations} />
          <DataRow label="컨펌">
            {number(confirmations)} / {number(requiredConfirmations)}회
          </DataRow>
        </>
      )}

      {txHash !== null && (
        <DataRow label="트랜잭션">
          <span style={{ wordBreak: 'break-all', fontSize: 12 }}>{txHash}</span>
        </DataRow>
      )}
      {blockNumber !== null && <DataRow label="블록">{number(blockNumber)}</DataRow>}
      {receiptStatus !== null && <DataRow label="영수증">{receiptStatus}</DataRow>}
      {assetAmount !== null && <DataRow label="입금 수량">{assetAmount}</DataRow>}
      {confirmedAt !== null && <DataRow label="확정 시각">{dateTime(confirmedAt)}</DataRow>}

      {failed && (
        <>
          <p className="ai-hint" role="alert">
            {failReason ?? '입금을 확인하지 못했습니다.'}
          </p>
          <Button variant="ghost" block className="mt16" onClick={onRetry}>
            다시 시도
          </Button>
        </>
      )}

      {!failed && (
        <p className="small muted mt16" style={{ marginBottom: 0, lineHeight: 1.8 }}>
          이 화면을 닫아도 입금 확인은 계속 진행됩니다. 거래관리에서 결과를 확인할 수 있습니다.
        </p>
      )}
    </Panel>
  )
}
