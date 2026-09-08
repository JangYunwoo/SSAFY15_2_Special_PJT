import { useSyncExternalStore } from 'react'

/**
 * 1초마다 갱신되는 공용 시계.
 *
 * 카운트다운이 여러 개 떠 있어도 타이머는 하나만 돕니다. 각 컴포넌트가
 * setInterval + setState 를 따로 들고 있으면 렌더가 흩어지고, 이펙트 안에서
 * 상태를 직접 바꾸게 되므로 외부 스토어로 빼서 구독합니다.
 */

const listeners = new Set<() => void>()
let timer: ReturnType<typeof setInterval> | null = null
let snapshot = Date.now()

function tick(): void {
  snapshot = Date.now()
  for (const listener of listeners) listener()
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  timer ??= setInterval(tick, 1000)

  return () => {
    listeners.delete(listener)
    if (listeners.size === 0 && timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }
}

function getSnapshot(): number {
  return snapshot
}

/** 서버 렌더에서는 구독 시점의 시각을 그대로 씁니다. */
function getServerSnapshot(): number {
  return snapshot
}

/** 현재 시각(ms). 1초마다 새 값으로 리렌더됩니다. */
export function useNow(): number {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)
}
