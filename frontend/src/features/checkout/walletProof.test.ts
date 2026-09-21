import { afterEach, describe, expect, it, vi } from 'vitest'
import { sendWithMetaMask, signWalletProof } from './metamask'
import type { CryptoQuote } from '@/entities/payment/model'
import { createCryptoSubmission } from './cryptoSubmission'
import { paymentApi } from '@/entities/payment/api'

const address = '0x' + 'a'.repeat(40)
const signature = '0x' + 'b'.repeat(130)
afterEach(() => vi.unstubAllGlobals())

describe('wallet proof', () => {
  it('uses UTF-8 hex personal_sign without sending funds', async () => {
    const request = vi.fn<(args: { method: string; params?: unknown[] }) => Promise<unknown>>()
      .mockResolvedValueOnce([address]).mockResolvedValueOnce('0x13882').mockResolvedValueOnce(signature)
    vi.stubGlobal('window', { ethereum: { request } })
    expect(await signWalletProof('공구한 proof', address, 80002)).toBe(signature)
    const hex = '0x' + Array.from(new TextEncoder().encode('공구한 proof'), (v) => v.toString(16).padStart(2, '0')).join('')
    expect(request.mock.calls[2]).toEqual([{ method: 'personal_sign', params: [hex, address] }])
    expect(request.mock.calls.map(([arg]) => arg.method)).not.toContain('eth_sendTransaction')
  })
  it('rejects another selected account before requesting signature', async () => {
    const request = vi.fn().mockResolvedValue(['0x' + 'c'.repeat(40)])
    vi.stubGlobal('window', { ethereum: { request } })
    await expect(signWalletProof('proof', address, 80002)).rejects.toThrow('계정')
    expect(request).toHaveBeenCalledTimes(1)
  })
  it('rejects the wrong chain', async () => {
    const request = vi.fn().mockResolvedValueOnce([address]).mockResolvedValueOnce('0x1')
    vi.stubGlobal('window', { ethereum: { request } })
    await expect(signWalletProof('proof', address, 80002)).rejects.toThrow('네트워크')
    expect(request).toHaveBeenCalledTimes(2)
  })
  it('shows signature rejection without submitting a transaction', async () => {
    const request = vi.fn().mockResolvedValueOnce([address]).mockResolvedValueOnce('0x13882').mockRejectedValueOnce({ code: 4001 })
    vi.stubGlobal('window', { ethereum: { request } })
    await expect(signWalletProof('proof', address, 80002)).rejects.toThrow('취소')
  })
  it('keeps the same challenge and signature after a lost response', async () => {
    const api = { ...paymentApi,
      cryptoWalletChallenge: vi.fn().mockResolvedValue({ challengeId: 'nonce', message: 'proof', chainId: 80002, expiresAt: '2999-01-01T00:00:00Z' }),
      submitCryptoTx: vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({ phase: 'CONFIRMING' }),
    }
    const sign = vi.fn().mockResolvedValue(signature)
    const submit = createCryptoSubmission(api, sign)
    const body = { quoteId: '1', fromAddress: address, txHash: '0x' + 'd'.repeat(64) }
    await expect(submit('1', body)).rejects.toThrow('network')
    await expect(submit('1', body)).resolves.toEqual({ phase: 'CONFIRMING' })
    expect(api.cryptoWalletChallenge).toHaveBeenCalledTimes(1)
    expect(sign).toHaveBeenCalledTimes(1)
    expect(api.submitCryptoTx.mock.calls[0]).toEqual(api.submitCryptoTx.mock.calls[1])
  })
  it('uses a new proof when payment changes and never submits after signing rejection', async () => {
    const api = { ...paymentApi,
      cryptoWalletChallenge: vi.fn().mockResolvedValue({ challengeId: 'nonce', message: 'proof', chainId: 80002, expiresAt: '2999-01-01T00:00:00Z' }),
      submitCryptoTx: vi.fn().mockResolvedValue({ phase: 'CONFIRMING' }),
    }
    const sign = vi.fn().mockRejectedValueOnce(new Error('cancel')).mockResolvedValue(signature)
    const submit = createCryptoSubmission(api, sign)
    const body = { quoteId: '1', fromAddress: address, txHash: '0x' + 'd'.repeat(64) }
    await expect(submit('1', body)).rejects.toThrow('cancel')
    expect(api.submitCryptoTx).not.toHaveBeenCalled()
    await submit('1', body)
    await submit('2', body)
    expect(api.cryptoWalletChallenge).toHaveBeenCalledTimes(3)
  })

  it('does not open a transfer for an expired quote', async () => {
    const request = vi.fn()
    vi.stubGlobal('window', { ethereum: { request } })
    const quote = { expiresAt: '2000-01-01T00:00:00Z' } as CryptoQuote
    await expect(sendWithMetaMask(quote)).rejects.toThrow('만료')
    expect(request).not.toHaveBeenCalled()
  })

  it('rechecks expiry after wallet interaction before sending', async () => {
    const quote = { expiresAt: '2999-01-01T00:00:00Z', chainId: 80002 } as CryptoQuote
    const request = vi.fn().mockResolvedValueOnce([address]).mockImplementationOnce(() => {
      quote.expiresAt = '2000-01-01T00:00:00Z'
      return Promise.resolve('0x13882')
    })
    vi.stubGlobal('window', { ethereum: { request } })
    await expect(sendWithMetaMask(quote)).rejects.toThrow('만료')
    expect(request).toHaveBeenCalledTimes(2)
  })
})
