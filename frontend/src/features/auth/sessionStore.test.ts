import { beforeEach, describe, expect, it } from 'vitest'

import type { AuthTokens } from '@/entities/auth/model'

import { useSessionStore } from './sessionStore'

const TOKENS: AuthTokens = {
  accessToken: 'access',
  refreshToken: 'refresh',
  tokenType: 'Bearer',
  expiresIn: 1800,
  actor: { id: '1', role: 'USER', isSeller: false, name: '테스터' },
}

beforeEach(() => {
  useSessionStore.setState({ accessToken: null, refreshToken: null, actor: null, expired: false })
})

describe('expireSession', () => {
  it('세션을 비우고 만료로 표시한다', () => {
    useSessionStore.getState().signIn(TOKENS)
    useSessionStore.getState().expireSession()

    const state = useSessionStore.getState()
    expect(state.accessToken).toBeNull()
    expect(state.refreshToken).toBeNull()
    expect(state.actor).toBeNull()
    expect(state.expired).toBe(true)
  })

  /**
   * 로그인하지 않은 채로 받은 401 까지 만료로 알리면, 처음 들어온 사람에게
   * "로그인이 만료되었습니다" 가 뜹니다.
   */
  it('로그인한 적이 없으면 만료로 표시하지 않는다', () => {
    useSessionStore.getState().expireSession()

    expect(useSessionStore.getState().expired).toBe(false)
  })
})

describe('만료 표시 해제', () => {
  it('다시 로그인하면 지워진다', () => {
    useSessionStore.getState().signIn(TOKENS)
    useSessionStore.getState().expireSession()
    useSessionStore.getState().signIn(TOKENS)

    expect(useSessionStore.getState().expired).toBe(false)
  })

  /** 스스로 로그아웃한 것은 만료가 아니므로 안내가 뜨면 안 됩니다. */
  it('직접 로그아웃하면 만료로 표시되지 않는다', () => {
    useSessionStore.getState().signIn(TOKENS)
    useSessionStore.getState().signOut()

    expect(useSessionStore.getState().expired).toBe(false)
  })

  it('만료 직후 로그아웃하면 남아 있던 표시도 지워진다', () => {
    useSessionStore.getState().signIn(TOKENS)
    useSessionStore.getState().expireSession()
    useSessionStore.getState().signOut()

    expect(useSessionStore.getState().expired).toBe(false)
  })

  it('안내를 보여준 뒤 clearExpired 로 지운다', () => {
    useSessionStore.getState().signIn(TOKENS)
    useSessionStore.getState().expireSession()
    useSessionStore.getState().clearExpired()

    expect(useSessionStore.getState().expired).toBe(false)
  })
})
