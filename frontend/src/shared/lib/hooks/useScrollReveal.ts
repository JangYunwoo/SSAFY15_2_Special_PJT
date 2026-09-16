import { useEffect, type RefObject } from 'react'

/**
 * 스크롤해서 화면에 들어온 요소를 한 번만 부드럽게 드러냅니다.
 *
 * 대상은 컨테이너 안의 .reveal 입니다.
 *
 * 안 보이게 만드는 일을 JS 가 켤 때만 하도록 했습니다. CSS 에서 바로 숨겨
 * 두면 스크립트가 실패하거나 늦게 뜰 때 내용이 영영 안 보입니다. 그래서
 * 컨테이너에 data-reveal 이 붙은 뒤에만 .reveal 이 투명해집니다.
 *
 * 처음부터 화면 안에 있는 요소는 숨기지 않고 바로 드러냅니다. 이유가 둘입니다.
 *   - 탭이 백그라운드면 IntersectionObserver 가 불리지 않습니다. 첫 화면까지
 *     숨겨두면 그동안 빈 화면을 보게 됩니다.
 *   - 첫 화면이 뒤늦게 떠오르면 열자마자 느린 인상을 줍니다. 등장 효과는
 *     스크롤해서 새로 만나는 블록에만 의미가 있습니다.
 *
 * 움직임을 줄이도록 설정한 사용자에게는 걸지 않습니다. 애니메이션이
 * 어지럼증을 유발하는 경우가 있어 운영체제 설정을 존중합니다.
 */
export function useScrollReveal(ref: RefObject<HTMLElement | null>): void {
  useEffect(() => {
    const root = ref.current
    if (root === null) return
    if (!('IntersectionObserver' in window)) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    const targets = [...root.querySelectorAll('.reveal')]

    /*
     * 첫 화면 몫을 data-reveal 보다 먼저 처리합니다. 순서가 뒤집히면 한
     * 프레임 동안 사라졌다가 나타나 깜빡입니다.
     */
    const belowFold: Element[] = []
    for (const target of targets) {
      if (target.getBoundingClientRect().top < window.innerHeight) {
        target.classList.add('is-in')
      } else {
        belowFold.push(target)
      }
    }

    root.dataset.reveal = 'on'

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          entry.target.classList.add('is-in')
          // 한 번 드러난 요소는 다시 숨기지 않습니다. 위아래로 스크롤할 때
          // 같은 블록이 계속 깜빡이면 읽는 데 방해가 됩니다.
          observer.unobserve(entry.target)
        }
      },
      // 아래에서 10% 쯤 올라왔을 때 시작합니다. 화면에 닿자마자 켜면
      // 이미 다 보이는 상태에서 뒤늦게 움직이는 것처럼 보입니다.
      { rootMargin: '0px 0px -10% 0px', threshold: 0.05 },
    )

    for (const target of belowFold) observer.observe(target)

    return () => {
      observer.disconnect()
      delete root.dataset.reveal
      for (const target of targets) target.classList.remove('is-in')
    }
  }, [ref])
}
