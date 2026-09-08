import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'

import { ApiError } from '@/shared/api/errors'

function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // 목록·상세는 1분 안에 다시 들어오면 캐시를 씁니다.
        staleTime: 60 * 1000,
        // 400번대는 다시 던져도 결과가 같으므로 재시도하지 않습니다.
        retry: (failureCount, error) => {
          if (error instanceof ApiError && error.isTerminal) return false
          return failureCount < 2
        },
        refetchOnWindowFocus: false,
      },
      mutations: {
        retry: false,
      },
    },
  })
}

export function QueryProvider({ children }: { children: ReactNode }) {
  // QueryClient 를 모듈 스코프에 두면 HMR·테스트에서 상태가 새어나갑니다.
  const [queryClient] = useState(createQueryClient)

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
