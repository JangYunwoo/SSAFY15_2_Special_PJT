import { useQuery } from '@tanstack/react-query'

import type { CryptoQuote } from '@/entities/payment/model'
import { paymentQueries } from '@/entities/payment/queries'
import { isTxHash, isWalletAddress } from '@/features/checkout/cryptoInput'
import { clock, number, won } from '@/shared/lib/format'
import { useCountdown } from '@/shared/lib/hooks/useCountdown'
import { DataRow } from '@/shared/ui/DataRow'
import { SelectField, TextField } from '@/shared/ui/Field'

interface CryptoFieldsProps {
  assetId: string | null
  onAssetChange: (assetId: string) => void
  walletAddress: string
  onWalletChange: (address: string) => void
  txHash: string
  onTxHashChange: (hash: string) => void
  quote: CryptoQuote | null
  isQuoteLoading: boolean
  onQuoteExpired: () => void
}

export function CryptoFields({
  assetId,
  onAssetChange,
  walletAddress,
  onWalletChange,
  txHash,
  onTxHashChange,
  quote,
  isQuoteLoading,
  onQuoteExpired,
}: CryptoFieldsProps) {
  const assets = useQuery(paymentQueries.cryptoAssets())

  const walletTyped = walletAddress.trim() !== ''
  const hashTyped = txHash.trim() !== ''

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
          value: asset.assetId,
          label: `${asset.symbol} · ${asset.chainName}`,
        }))}
      />

      {quote !== null && <QuotePanel quote={quote} onExpired={onQuoteExpired} />}
      {quote === null && isQuoteLoading && (
        <p className="small muted mt16">환율 정보를 불러오는 중입니다.</p>
      )}

      <TextField
        label="보내는 지갑 주소"
        required
        placeholder="0x71A...92F"
        autoComplete="off"
        value={walletAddress}
        onChange={(event) => {
          onWalletChange(event.target.value)
        }}
        hint="0x 로 시작하는 40자리 주소"
        error={
          walletTyped && !isWalletAddress(walletAddress)
            ? '주소 형식이 올바르지 않습니다.'
            : undefined
        }
      />

      <TextField
        label="트랜잭션 해시"
        required
        placeholder="0x 로 시작하는 64자리"
        autoComplete="off"
        value={txHash}
        onChange={(event) => {
          onTxHashChange(event.target.value)
        }}
        hint="위 주소로 송금한 뒤 지갑에서 복사해 붙여 주세요."
        error={hashTyped && !isTxHash(txHash) ? '해시 형식이 올바르지 않습니다.' : undefined}
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
        <br />
        {/* 코인 수량만으로는 얼마를 내는지 가늠하기 어렵습니다. 서버가 확정한
            원화 금액을 함께 적습니다. 청구·환불도 이 금액이 기준입니다. */}
        <span className="small muted">≈ {won(quote.krwAmount)}</span>
      </DataRow>
      <DataRow label="필요 컨펌">{number(quote.requiredConfirmations)}회</DataRow>

      {/* 어디로 보내야 하는지가 가장 중요한 정보라 따로 강조합니다. */}
      <DataRow label="입금 주소">
        <span style={{ wordBreak: 'break-all', fontSize: 12 }}>{quote.depositAddress}</span>
      </DataRow>

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
