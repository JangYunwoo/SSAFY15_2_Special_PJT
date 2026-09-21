import { paymentApi } from '@/entities/payment/api'
import type { CryptoTransactionRequest, CryptoWalletChallengeRequest } from '@/entities/payment/model'
import { signWalletProof } from './metamask'

/** 응답 유실 시 동일 nonce/서명을 재사용한다. 보안 증명은 메모리에만 둔다. */
export function createCryptoSubmission(api = paymentApi, sign = signWalletProof) {
  const proofs = new Map<string, { body: CryptoTransactionRequest; expiresAt: string }>()
  return async (paymentId: string, request: CryptoWalletChallengeRequest) => {
    const key = JSON.stringify([paymentId, request.quoteId, request.fromAddress.toLowerCase(), request.txHash.toLowerCase()])
    let proof = proofs.get(key)
    if (proof === undefined) {
      const challenge = await api.cryptoWalletChallenge(paymentId, request)
      const signature = await sign(challenge.message, request.fromAddress, challenge.chainId)
      proof = { body: { ...request, challengeId: challenge.challengeId, signature }, expiresAt: challenge.expiresAt }
      proofs.set(key, proof)
    }
    try {
      return await api.submitCryptoTx(paymentId, proof.body)
    } catch (error) {
      // 만료 뒤에도 첫 재전송은 같은 증명으로 응답 복구를 시도한다.
      // 서버가 거부하거나 재전송에 실패하면 다음 사용자 재시도에서 새 증명을 발급한다.
      if (Date.parse(proof.expiresAt) <= Date.now()) proofs.delete(key)
      throw error
    }
  }
}
