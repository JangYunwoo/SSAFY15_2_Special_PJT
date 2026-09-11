import { Suspense } from 'react'
import { RouterProvider } from 'react-router'

import { LoadingState } from '@/shared/ui/states'

import { ErrorBoundary } from './providers/ErrorBoundary'
import { QueryProvider } from './providers/QueryProvider'
import { router } from './router'

export function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <Suspense fallback={<LoadingState label="화면을 준비하고 있습니다." />}>
          <RouterProvider router={router} />
        </Suspense>
      </QueryProvider>
    </ErrorBoundary>
  )
}
