import { Component, type ErrorInfo, type ReactNode } from 'react'

import { toMessage } from '@/shared/api/errors'
import { Button } from '@/shared/ui/Button'

interface Props {
  children: ReactNode
}

interface State {
  error: unknown
}

/**
 * 렌더 중 터진 예외를 잡는 최상위 경계.
 * react-error-boundary 를 추가로 넣지 않고 클래스 컴포넌트 하나로 처리합니다.
 */
export class ErrorBoundary extends Component<Props, State> {
  override state: State = { error: null }

  static getDerivedStateFromError(error: unknown): State {
    return { error }
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  private readonly reset = (): void => {
    this.setState({ error: null })
  }

  override render(): ReactNode {
    const { error } = this.state
    if (error === null) return this.props.children

    return (
      <main className="wrap">
        <section className="result">
          <h2>화면을 표시할 수 없습니다</h2>
          <p>{toMessage(error)}</p>
          <Button block onClick={this.reset}>
            다시 시도
          </Button>
        </section>
      </main>
    )
  }
}
