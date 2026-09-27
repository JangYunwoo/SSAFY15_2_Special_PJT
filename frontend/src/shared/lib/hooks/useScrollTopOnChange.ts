import { useEffect, useRef } from 'react'

/**
 * 값이 바뀔 때마다 화면을 맨 위로 올립니다.
 *
 * 주소는 그대로 두고 화면 안에서 단계만 바꾸는 흐름(판매자 전환 등)에 씁니다.
 * 주소가 바뀌는 이동은 `AppShell` 의 `ScrollRestoration` 이 맡으므로 여기서는
 * 다루지 않습니다. 처음 그릴 때는 건드리지 않습니다.
 */
export function useScrollTopOnChange(value: unknown): void {
  const previous = useRef(value)

  useEffect(() => {
    if (Object.is(previous.current, value)) return
    previous.current = value
    window.scrollTo({ top: 0 })
  }, [value])
}
