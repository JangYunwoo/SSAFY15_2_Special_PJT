/**
 * MetaMask 로 ERC-20 토큰을 송금합니다 (CRYP-02 -> CRYP-03).
 *
 * 사용자가 지갑 앱에서 주소와 금액을 손으로 입력하면 오타 한 글자에 금액이 어긋나고,
 * 백엔드는 견적과 정확히 일치하는 입금만 통과시킵니다. 여기서 Quote 값으로 호출
 * 데이터를 만들어 넘기면 사용자는 MetaMask 에서 확인만 누르면 됩니다.
 *
 * 주소·해시 입력칸은 직접 송금한 거래를 등록할 때도 사용합니다. 단, 등록 단계에서
 * 송신 지갑으로 MetaMask 소유권 서명이 필요합니다. 거래소 출금 주소는 지원하지 않습니다.
 */
import type { CryptoQuote } from '@/entities/payment/model'

/** transfer(address,uint256) 의 함수 선택자. */
const TRANSFER_SELECTOR = 'a9059cbb'

/** ERC-20 전송에 넉넉한 가스 한도. 안 쓴 만큼은 돌려받습니다. */
const GAS_LIMIT = '0x1d4c0' // 120,000

/**
 * Polygon 은 최소 25 gwei 를 요구합니다.
 *
 * 지정하지 않으면 MetaMask 가 이더리움 기준(1.5 gwei)으로 잡아 노드가 거부합니다
 * ("transaction gas price below minimum: gas tip cap 1500000000").
 * maxFee 는 상한일 뿐이라 실제로는 그때의 시장가만 빠져나갑니다.
 */
const MAX_PRIORITY_FEE = '0x6fc23ac00' // 30 gwei
const MAX_FEE = '0x12a05f2000' // 80 gwei

interface Eip1193Provider {
  request: (args: { method: string; params?: unknown[] }) => Promise<unknown>
}

function getProvider(): Eip1193Provider {
  const eth = (window as unknown as { ethereum?: Eip1193Provider }).ethereum
  if (eth === undefined) {
    throw new Error('MetaMask 가 설치되어 있지 않습니다.')
  }
  return eth
}

/** 송금과 별개인 EIP-191 소유권 증명. 개인키는 지갑 밖으로 나오지 않습니다. */
export async function signWalletProof(message: string, address: string, chainId: number): Promise<string> {
  const eth = getProvider()
  const accounts = await eth.request({ method: 'eth_requestAccounts' })
  if (!Array.isArray(accounts) || typeof accounts[0] !== 'string' ||
      accounts[0].toLowerCase() !== address.toLowerCase()) {
    throw new Error('송금한 지갑 계정으로 MetaMask를 전환해 주세요.')
  }
  const currentChain = await eth.request({ method: 'eth_chainId' })
  if (typeof currentChain !== 'string' || BigInt(currentChain) !== BigInt(chainId)) {
    throw new Error('송금한 네트워크로 MetaMask를 전환해 주세요.')
  }
  const hex = '0x' + Array.from(new TextEncoder().encode(message),
    (byte) => byte.toString(16).padStart(2, '0')).join('')
  try {
    const signature = await eth.request({ method: 'personal_sign', params: [hex, address] })
    if (typeof signature !== 'string' || !/^0x[0-9a-fA-F]{130}$/.test(signature)) {
      throw new Error('지갑 서명 응답이 올바르지 않습니다.')
    }
    return signature
  } catch (error) {
    throw new Error(toMetaMaskMessage(error), { cause: error })
  }
}

/**
 * ERC-20 `transfer(address,uint256)` 호출 데이터를 만듭니다.
 *
 * 주소와 금액을 각각 32바이트로 왼쪽 0 채움 합니다. 금액은 `BigInt` 로 다룹니다 —
 * `Number` 는 배정밀도라 큰 정수에서 값이 뭉개집니다.
 */
export function encodeTransfer(to: string, rawAmount: string): string {
  const address = to.toLowerCase().replace(/^0x/, '').padStart(64, '0')
  const amount = BigInt(rawAmount).toString(16).padStart(64, '0')
  return `0x${TRANSFER_SELECTOR}${address}${amount}`
}

/**
 * Quote 대로 송금하고 트랜잭션 해시를 돌려줍니다.
 *
 * <b>금액은 `quote.rawAmount` 를 그대로 씁니다.</b> `assetAmount` 에 10^decimals 를
 * 곱해 다시 만들면 부동소수점 때문에 1 wei 어긋날 수 있고, 그러면 백엔드가
 * "입금액이 견적과 다릅니다" 로 거부합니다.
 *
 * @returns 실제로 보낸 지갑 주소와 트랜잭션 해시. 두 입력칸을 자동으로 채웁니다
 */
export async function sendWithMetaMask(
  quote: CryptoQuote,
): Promise<{ from: string; txHash: string }> {
  const eth = getProvider()
  const ensureQuoteValid = () => {
    if (!Number.isFinite(Date.parse(quote.expiresAt)) || Date.parse(quote.expiresAt) <= Date.now()) {
      throw new Error('환율 정보가 만료되어 송금할 수 없습니다. 다시 발급해 주세요.')
    }
  }
  ensureQuoteValid()

  const accounts = (await eth.request({ method: 'eth_requestAccounts' })) as string[]
  const from = accounts[0]
  if (from === undefined) {
    throw new Error('연결된 지갑 계정이 없습니다.')
  }

  // 다른 체인에서 보내면 같은 주소라도 우리 지갑이 아닙니다. 반드시 확인합니다.
  const chainId = (await eth.request({ method: 'eth_chainId' })) as string
  const targetChainId = '0x' + quote.chainId.toString(16)
  if (chainId !== targetChainId) {
    await eth.request({
      method: 'wallet_switchEthereumChain',
      params: [{ chainId: targetChainId }],
    })
  }

  ensureQuoteValid()
  const txHash = (await eth.request({
    method: 'eth_sendTransaction',
    params: [
      {
        from,
        to: quote.tokenContract,
        data: encodeTransfer(quote.depositAddress, quote.rawAmount),
        gas: GAS_LIMIT,
        maxPriorityFeePerGas: MAX_PRIORITY_FEE,
        maxFeePerGas: MAX_FEE,
      },
    ],
  })) as string

  return { from, txHash }
}

/**
 * 지갑이 돌려준 오류를 사용자가 할 수 있는 행동으로 바꿉니다.
 *
 * 그대로 노출하면 "User denied transaction signature" 같은 영어가 뜨고, 사용자는
 * 자기가 취소한 것인지 시스템 오류인지 구분하지 못합니다.
 */
export function toMetaMaskMessage(error: unknown): string {
  const e = (error ?? {}) as { code?: number; message?: string }

  if (e.code === 4001) return '지갑에서 요청을 취소했습니다.'
  if (e.code === 4902) return 'MetaMask 에 Polygon Amoy 네트워크를 추가해 주세요.'

  const message = e.message ?? ''
  if (message.includes('insufficient funds')) {
    return '가스비로 쓸 POL 이 부족합니다.'
  }
  if (message.includes('gas price below minimum')) {
    return '가스비가 네트워크 최소값보다 낮습니다. 잠시 후 다시 시도해 주세요.'
  }
  return message !== '' ? message : '송금에 실패했습니다.'
}
