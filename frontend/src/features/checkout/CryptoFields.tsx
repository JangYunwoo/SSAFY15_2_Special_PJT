import { useQuery } from '@tanstack/react-query'

import type { CryptoQuote } from '@/entities/payment/model'
import { paymentQueries } from '@/entities/payment/queries'
import { clock, number, won } from '@/shared/lib/format'
import { useCountdown } from '@/shared/lib/hooks/useCountdown'
import { DataRow } from '@/shared/ui/DataRow'
import { SelectField, TextField } from '@/shared/ui/Field'

interface CryptoFieldsProps {
  assetId: string | null
  onAssetChange: (assetId: string) => void
  walletAddress: string
  onWalletChange: (address: string) => void
  quote: CryptoQuote | null
  isQuoteLoading: boolean
  onQuoteExpired: () => void
}

export function CryptoFields({
  assetId,
  onAssetChange,
  walletAddress,
  onWalletChange,
  quote,
  isQuoteLoading,
  onQuoteExpired,
}: CryptoFieldsProps) {
  const assets = useQuery(paymentQueries.cryptoAssets())

  return (
    <>
      <SelectField
        label="결제할 코인"
        placeholder="자산 선택"
        value={assetId ?? ''}
        onChange={(event) => {
          onAssetChange(event.target.value)
        }}
        options={(assets.data ?? []).map((asset) => ({
          value: asset.id,
          label: `${asset.symbol} · ${asset.name}`,
        }))}
      />

      {quote !== null && <QuotePanel quote={quote} onExpired={onQuoteExpired} />}
      {quote === null && isQuoteLoading && (
        <p className="small muted mt16">환율 정보를 불러오는 중입니다.</p>
      )}

      <TextField
        label="지갑 주소"
        placeholder="0x71A...92F"
        autoComplete="off"
        value={walletAddress}
        onChange={(event) => {
          onWalletChange(event.target.value)
        }}
      />

      <div className="box mt16" style={{ fontSize: 12, lineHeight: 1.7 }}>
        KRW 기준 거래가치로 기록되며, 환불도 이 금액을 기준으로 산정됩니다. Quote 유효시간이 지나면
        환율이 다시 고정됩니다.
      </div>
    </>
  )
}

function QuotePanel({ quote, onExpired }: { quote: CryptoQuote; onExpired: () => void }) {
  const { secondsLeft, isExpired } = useCountdown(quote.expiresAt)

  return (
    <div className="quote mt16">
      <div className="quote-head">
        <span>적용 환율 (Quote)</span>
        <span className="quote-clock">{clock(secondsLeft)}</span>
      </div>
      <DataRow label="시장 환율">{won(Number(quote.marketRate))}</DataRow>
      <DataRow label="Buffer">{(Number(quote.bufferRate) * 100).toFixed(2)}%</DataRow>
      <DataRow label="적용 환율">{won(Number(quote.appliedRate))}</DataRow>
      <DataRow label="결제 수량">
        {Number(quote.assetAmount).toFixed(8)} {quote.asset}
      </DataRow>
      <DataRow label="필요 컨펌">{number(quote.requiredConfirmations)}회</DataRow>

      {isExpired && (
        <p className="ai-hint" role="alert">
          환율 정보가 만료되었습니다.{' '}
          <button type="button" className="tlink" onClick={onExpired}>
            다시 발급하기
          </button>
        </p>
      )}
    </div>
  )
}
