import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'

import { authApi } from '@/entities/auth/api'
import type { BuyerSignupRequest, LoginRequest, SellerSignupRequest } from '@/entities/auth/model'

import { useSessionStore } from './sessionStore'

/** 로그인 성공 후 이동할 기본 경로 */
const HOME_BY_ROLE = {
  BUYER: '/',
  SELLER: '/seller',
  ADMIN: '/',
} as const

export function useLogin(role: 'buyer' | 'seller') {
  const signIn = useSessionStore((state) => state.signIn)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (body: LoginRequest) =>
      role === 'buyer' ? authApi.loginBuyer(body) : authApi.loginSeller(body),
    onSuccess: async (tokens) => {
      signIn(tokens)
      // 이전 사용자의 캐시가 남지 않도록 비웁니다.
      await queryClient.resetQueries()
      await navigate(HOME_BY_ROLE[tokens.actor.role], { replace: true })
    },
  })
}

export function useSignupBuyer() {
  const signIn = useSessionStore((state) => state.signIn)
  const navigate = useNavigate()

  return useMutation({
    mutationFn: (body: BuyerSignupRequest) => authApi.signupBuyer(body),
    onSuccess: async (tokens) => {
      signIn(tokens)
      await navigate('/', { replace: true })
    },
  })
}

export function useSignupSeller() {
  const signIn = useSessionStore((state) => state.signIn)
  const navigate = useNavigate()

  return useMutation({
    mutationFn: (body: SellerSignupRequest) => authApi.signupSeller(body),
    onSuccess: async (tokens) => {
      signIn(tokens)
      await navigate('/seller/verify', { replace: true })
    },
  })
}

export function useLogout() {
  const signOut = useSessionStore((state) => state.signOut)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => authApi.logout(),
    // 서버 폐기가 실패해도 클라이언트 세션은 반드시 정리합니다.
    onSettled: async () => {
      signOut()
      queryClient.clear()
      await navigate('/login', { replace: true })
    },
  })
}
