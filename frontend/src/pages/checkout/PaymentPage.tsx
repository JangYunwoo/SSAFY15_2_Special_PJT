import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'

import type { PaymentMethod } from '@/entities/model'
import type { GroupBuyDetail } from '@/entities/groupBuy/model'
import { groupBuyQueries } from '@/entities/groupBuy/queries'
import { paymentApi } from '@/entities/payment/api'
import type { CryptoQuote } from '@/entities/payment/model'
import { CardFields } from '@/features/checkout/CardFields'
import { EMPTY_CARD, isCardComplete, type CardInput } from '@/features/checkout/cardInput'
import { useCheckoutStore } from '@/features/checkout/checkoutStore'
import { CryptoFields } from '@/features/checkout/CryptoFields'
import { isTxHash, isWalletAddress } from '@/features/checkout/cryptoInput'
import { CryptoWaiting } from '@/features/checkout/CryptoWaiting'
import { OrderSummary } from '@/features/checkout/OrderSummary'
import { PaymentMethodPicker } from '@/features/checkout/PaymentMethodPicker'
import { SlotTimer } from '@/features/checkout/SlotTimer'
import { usePaymentSlot } from '@/features/checkout/usePaymentSlot'
import { toMessage } from '@/shared/api/errors'
import { won } from '@/shared/lib/format'
import { useDocumentTitle } from '@/shared/lib/hooks/useDocumentTitle'
import { Button } from '@/shared/ui/Button'
import { Steps } from '@/shared/ui/Steps'
import { QueryBoundary } from '@/shared/ui/states'

import { CHECKOUT_STEPS } from './steps'

export default function PaymentPage() {
  useDocumentTitle('결제수단 선택')

  const { groupBuyId = '' } = useParams()
  const groupBuy = useQuery(groupBuyQueries.detail(groupBuyId))

  return (
    <main className="wrap">
      <Steps steps={CHECKOUT_STEPS} current={1} />

      <section className="section" style={{ paddingTop: 32 }}>
        <QueryBoundary query={groupBuy}>
          {(detail) => <PaymentForm groupBuy={detail} />}
        </QueryBoundary>
      </section>
    </main>
  )
}

function PaymentForm({ groupBuy }: { groupBuy: GroupBuyDetail }) {
  const navigate = useNavigate()
  const addressId = useCheckoutStore((state) => state.addressId)

  const [method, setMethod] = useState<PaymentMethod>('CARD')
  const [card, setCard] = useState<CardInput>(EMPTY_CARD)
  const [cryptoAssetId, setCryptoAssetId] = useState<string | null>(null)
  const [walletAddress, setWalletAddress] = useState('')
  const [txHash, setTxHash] = useState('')
  /** 해시를 등록한 뒤 입금 확인을 기다리는 결제. 코인 결제만 이 단계를 거칩니다. */
  const [awaitingPaymentId, setAwaitingPaymentId] = useState<string | null>(null)
  const [quote, setQuote] = useState<CryptoQuote | null>(null)

  const slot = usePaymentSlot({
    groupBuyId: groupBuy.id,
    addressId,
    method,
    cryptoAssetId: method === 'CRYPTO' ? cryptoAssetId : null,
  })
  const payment = slot.payment

  /** CRYP-02. 자산을 고르면 환율을 고정합니다. */
  const issueQuote = useMutation({
    mutationFn: ({ paymentId, assetId }: { paymentId: string; assetId: string }) =>
      paymentApi.cryptoQuote(paymentId, assetId),
    onSuccess: setQuote,
  })

  /** CARD-01 / KAKA-01. 승인에 성공하면 완료 화면으로 넘어갑니다. */
  const approve = useMutation({
    mutationFn: async () => {
      if (payment === null) throw new Error('결제 준비가 완료되지 않았습니다.')

      if (method === 'CARD') {
        return paymentApi.approveCard(payment.paymentId, {
          cardNumber: card.cardNumber.replace(/\s/g, ''),
          cvc: card.cvc,
        })
      }

      if (method === 'KAKAOPAY') {
        const ready = await paymentApi.kakaoReady(payment.paymentId)
        // 외부 결제창으로 이동하면 이 화면이 언마운트된다. 이동 전에 정산 완료로
        // 표시하지 않으면 cleanup이 PAY-03을 호출해 방금 준비한 슬롯을 해제한다.
        slot.markSettled()
        window.location.assign(ready.nextRedirectPcUrl)
        return null
      }

      if (quote === null) throw new Error('환율 정보를 먼저 발급해 주세요.')
      /*
       * CRYP-03. 해시를 등록해도 결제 완료가 아닙니다. 필요 컨펌 수에 도달해야
       * 확정되므로 완료 화면으로 바로 넘기지 않고 입금 확인 단계로 넘어갑니다.
       */
      await paymentApi.submitCryptoTx(payment.paymentId, {
        quoteId: quote.quoteId,
        txHash: txHash.trim(),
        fromAddress: walletAddress.trim(),
      })
      setAwaitingPaymentId(payment.paymentId)
      return null
    },
    onSuccess: (result) => {
      // 승인된 결제는 이 화면을 벗어날 때 해제하지 않습니다.
      slot.markSettled()
      setCard(EMPTY_CARD)
      if (result !== null)
        void navigate(`/checkout/complete/${result.paymentId}`, { replace: true })
    },
  })

  const selectAsset = (assetId: string) => {
    setCryptoAssetId(assetId)
    setQuote(null)
    if (payment !== null) issueQuote.mutate({ paymentId: payment.paymentId, assetId })
  }

  const canSubmit =
    payment !== null &&
    !approve.isPending &&
    (method === 'CARD'
      ? isCardComplete(card)
      : method === 'KAKAOPAY'
        ? true
        : quote !== null && isWalletAddress(walletAddress) && isTxHash(txHash))

  // 서버가 확정한 1인 결제금액을 그대로 씁니다 (GRP-01/02 personalPaymentAmount).
  const total = groupBuy.personalPaymentAmount

  if (addressId === null) {
    return (
      <div className="panel">
        <h3>배송지를 먼저 선택해 주세요</h3>
        <p className="sub">배송지 단계에서 받는 주소를 고른 뒤 결제할 수 있습니다.</p>
        <Button
          className="mt16"
          onClick={() => {
            void navigate(`/checkout/${groupBuy.id}/shipping`, { replace: true })
          }}
        >
          배송지 단계로 이동
        </Button>
      </div>
    )
  }

  if (awaitingPaymentId !== null) {
    return (
      <div className="split">
        <div>
          <CryptoWaiting
            paymentId={awaitingPaymentId}
            onCompleted={() => {
              void navigate(`/checkout/complete/${awaitingPaymentId}`, { replace: true })
            }}
            onRetry={() => {
              setAwaitingPaymentId(null)
              setTxHash('')
            }}
          />
        </div>
        <aside className="sticky">
          <OrderSummary groupBuy={groupBuy} />
        </aside>
      </div>
    )
  }

  return (
    <div className="split">
      <div>
        <div className="panel">
          <h3>결제수단</h3>
          <p className="sub">결제 금액은 구매확정 전까지 에스크로에 보관됩니다</p>

          <PaymentMethodPicker
            value={method}
            onChange={(next) => {
              setMethod(next)
              setQuote(null)
            }}
            bodies={{
              CARD: <CardFields value={card} onChange={setCard} />,
              KAKAOPAY: (
                <p className="small muted" style={{ margin: 0, lineHeight: 1.8 }}>
                  카카오페이 결제 화면으로 이동해 결제를 진행합니다. 결제가 끝나면 이 화면으로
                  돌아옵니다.
                </p>
              ),
              CRYPTO: (
                <CryptoFields
                  assetId={cryptoAssetId}
                  onAssetChange={selectAsset}
                  walletAddress={walletAddress}
                  onWalletChange={setWalletAddress}
                  txHash={txHash}
                  onTxHashChange={setTxHash}
                  quote={quote}
                  isQuoteLoading={issueQuote.isPending}
                  onQuoteExpired={() => {
                    if (cryptoAssetId !== null) selectAsset(cryptoAssetId)
                  }}
                />
              ),
            }}
          />
        </div>
      </div>

      <aside className="sticky">
        <OrderSummary groupBuy={groupBuy}>
          {slot.error !== null && (
            <p className="ai-hint" role="alert">
              {toMessage(slot.error)}
            </p>
          )}
          {approve.isError && (
            <p className="ai-hint" role="alert">
              {toMessage(approve.error)}
            </p>
          )}

          <Button
            block
            className="mt16"
            disabled={!canSubmit}
            onClick={() => {
              approve.mutate()
            }}
          >
            {slot.isPreparing
              ? '결제 준비 중…'
              : approve.isPending
                ? '결제 처리 중…'
                : `${won(total)} 결제하기`}
          </Button>

          {payment !== null && (
            <SlotTimer
              expiresAt={payment.slotExpiresAt}
              onExpire={() => {
                void navigate(`/group-buys/${groupBuy.id}`, { replace: true })
              }}
            />
          )}
        </OrderSummary>
      </aside>
    </div>
  )
}
