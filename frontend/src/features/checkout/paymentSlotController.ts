import type { PaymentMethod } from '@/entities/model'
import type { Payment, PreparePaymentRequest } from '@/entities/payment/model'
import { ApiError } from '@/shared/api/errors'

export interface SlotSelection {
  deliveryRequest?: string | null
  groupBuyId: string
  addressId: string
  method: PaymentMethod
  cryptoAssetId: string | null
}

interface Ports {
  prepare: (groupBuyId: string, body: PreparePaymentRequest, key: string) => Promise<Payment>
  abort: (paymentId: string) => Promise<unknown>
  newKey: () => string
  now: () => number
}

interface Attempt {
  selection: SlotSelection
  key: string
  revision: number
}

export interface SlotSnapshot {
  payment: Payment | null
  selection: SlotSelection | null
  busy: boolean
  error: unknown
}

export function selectionKey(selection: SlotSelection | null): string {
  return JSON.stringify(selection && [selection.groupBuyId, selection.addressId,
    selection.method, selection.cryptoAssetId, selection.deliveryRequest ?? null])
}

export function isUsablePayment(
  payment: Payment | null, selection: SlotSelection | null, now: number,
): boolean {
  return payment !== null && selection !== null &&
    payment.groupBuyId === selection.groupBuyId && payment.method === selection.method &&
    payment.status === 'PROCESSING' && Date.parse(payment.slotExpiresAt) > now
}

/**
 * 한 번에 한 네트워크 작업만 수행한다. prepare 응답을 잃어도 같은 키로 확인한 뒤
 * 해제하므로 요청 취소를 슬롯 취소로 오해하지 않는다. React와 분리해 경합을 검증한다.
 */
export class PaymentSlotController {
  private readonly ports: Ports
  private desired: SlotSelection | null = null
  private revision = 0
  private attempt: Attempt | null = null
  private held: { payment: Payment; attempt: Attempt } | null = null
  private running = false
  private readonly listeners = new Set<() => void>()
  private snapshot: SlotSnapshot = { payment: null, selection: null, busy: false, error: null }

  constructor(ports: Ports) { this.ports = ports }

  getSnapshot = (): SlotSnapshot => this.snapshot
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => { this.listeners.delete(listener) }
  }

  select = (selection: SlotSelection | null): void => {
    if (selectionKey(selection) === selectionKey(this.desired)) return
    this.desired = selection
    this.revision += 1
    this.publish(null, true, null)
    this.start()
  }

  retry = (): void => {
    if (this.running) return
    // 불명확한 prepare는 attempt의 키를 유지한다. 종료된 attempt는 이미 제거됐다.
    this.publish(null, true, null)
    this.start()
  }

  /** 승인 완료 또는 외부 결제/입금 확인 화면에 소유권을 넘긴다. abort하지 않는다. */
  handoff = (): void => {
    this.held = null
    this.attempt = null
    this.desired = null
    this.revision += 1
    this.publish(null, false, null)
  }

  private publish(payment: Payment | null, busy: boolean, error: unknown): void {
    this.snapshot = { payment, selection: this.desired, busy, error }
    for (const listener of this.listeners) listener()
  }

  private start(): void {
    if (this.running) return
    this.running = true
    void this.reconcile().finally(() => {
      this.running = false
      // publish 이후 finally 이전에 새 선택이 들어와도 작업이 유실되지 않는다.
      if (this.snapshot.busy) this.start()
    })
  }

  private async reconcile(): Promise<void> {
    try {
      for (;;) {
        if (this.held !== null) {
          const { payment, attempt } = this.held
          if (attempt.revision === this.revision &&
              isUsablePayment(payment, this.desired, this.ports.now())) {
            this.publish(payment, false, null)
            return
          }
          // 성공 응답을 받을 때까지 held를 버리지 않는다. 실패하면 새 prepare를 막는다.
          await this.ports.abort(payment.paymentId)
          this.held = null
        }
        if (this.attempt === null) {
          if (this.desired === null) {
            this.publish(null, false, null)
            return
          }
          this.attempt = {
            selection: this.desired, revision: this.revision, key: this.ports.newKey(),
          }
        }
        const attempt = this.attempt
        const { groupBuyId, ...body } = attempt.selection
        let payment: Payment
        try {
          payment = await this.ports.prepare(groupBuyId, body, attempt.key)
        } catch (error) {
          // 명확하게 거절한 요청만 끝낸다. 통신 실패/5xx는 같은 키로 확인한다.
          if (error instanceof ApiError && error.isTerminal) this.attempt = null
          throw error
        }
        this.attempt = null
        if (payment.status !== 'PROCESSING') {
          throw new Error('이미 종료된 결제입니다. 결제 내역을 확인한 뒤 다시 시도해 주세요.')
        }
        this.held = { payment, attempt }
        if (!isUsablePayment(payment, attempt.selection, this.ports.now())) {
          await this.ports.abort(payment.paymentId)
          this.held = null
          throw new Error('결제 준비 정보가 만료되었거나 선택과 다릅니다. 다시 준비해 주세요.')
        }
        // 선택 변경/화면 종료 시 다음 반복에서 뒤늦게 생성된 슬롯부터 해제한다.
      }
    } catch (error) {
      this.publish(null, false, error)
    }
  }
}
