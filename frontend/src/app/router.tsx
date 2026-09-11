/* eslint-disable react-refresh/only-export-components --
   라우터는 컴포넌트가 아니라 라우트 설정(router)을 내보내는 모듈입니다. */
import { lazy } from 'react'
import { createBrowserRouter } from 'react-router'

import { CheckoutLayout } from './layouts/CheckoutLayout'
import { RequireAuth, RequireSeller } from './layouts/guards'
import { RootLayout } from './layouts/RootLayout'
import { SellerLayout } from './layouts/SellerLayout'

/* 첫 화면 두 개만 즉시 번들에 넣고, 나머지는 라우트 단위로 쪼갭니다. */
import { GroupBuyListPage } from '@/pages/GroupBuyListPage'
import { HomePage } from '@/pages/HomePage'

const AboutPage = lazy(() => import('@/pages/AboutPage'))
const GroupBuyDetailPage = lazy(() => import('@/pages/GroupBuyDetailPage'))
const LoginPage = lazy(() => import('@/pages/LoginPage'))
const SignupPage = lazy(() => import('@/pages/SignupPage'))
const MyPage = lazy(() => import('@/pages/MyPage'))
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))

const ShippingPage = lazy(() => import('@/pages/checkout/ShippingPage'))
const PaymentPage = lazy(() => import('@/pages/checkout/PaymentPage'))
const CompletePage = lazy(() => import('@/pages/checkout/CompletePage'))
const KakaoPayCallbackPage = lazy(() => import('@/pages/checkout/KakaoPayCallbackPage'))

const OrdersPage = lazy(() => import('@/pages/orders/OrdersPage'))
const OrdersPaymentsPage = lazy(() => import('@/pages/orders/OrdersPaymentsPage'))
const ConfirmPage = lazy(() => import('@/pages/orders/ConfirmPage'))
const ReturnRequestPage = lazy(() => import('@/pages/orders/ReturnRequestPage'))

const SellerVerifyPage = lazy(() => import('@/pages/seller/SellerVerifyPage'))
const SellerHomePage = lazy(() => import('@/pages/seller/SellerHomePage'))
const SellerProductsPage = lazy(() => import('@/pages/seller/SellerProductsPage'))
const SellerProductNewPage = lazy(() => import('@/pages/seller/SellerProductNewPage'))
const SellerProductEditPage = lazy(() => import('@/pages/seller/SellerProductEditPage'))
const SellerGroupBuyNewPage = lazy(() => import('@/pages/seller/SellerGroupBuyNewPage'))
const SellerGroupBuyDetailPage = lazy(() => import('@/pages/seller/SellerGroupBuyDetailPage'))
const SellerOrdersPage = lazy(() => import('@/pages/seller/SellerOrdersPage'))
const SellerReturnsPage = lazy(() => import('@/pages/seller/SellerReturnsPage'))
const SellerSettlementsPage = lazy(() => import('@/pages/seller/SellerSettlementsPage'))

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'group-buys', element: <GroupBuyListPage /> },
      { path: 'group-buys/:groupBuyId', element: <GroupBuyDetailPage /> },
      { path: 'about', element: <AboutPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },

      /* 로그인 필요 */
      {
        element: <RequireAuth />,
        children: [
          { path: 'me', element: <MyPage /> },
          { path: 'orders', element: <OrdersPage /> },
          { path: 'orders/payments', element: <OrdersPaymentsPage /> },
          { path: 'orders/:participationId/confirm', element: <ConfirmPage /> },
          { path: 'orders/:participationId/return', element: <ReturnRequestPage /> },
        ],
      },
    ],
  },

  /* 결제 플로우는 헤더가 다릅니다 */
  {
    element: <CheckoutLayout />,
    children: [
      {
        element: <RequireAuth />,
        children: [
          { path: 'checkout/:groupBuyId/shipping', element: <ShippingPage /> },
          { path: 'checkout/:groupBuyId/payment', element: <PaymentPage /> },
          { path: 'checkout/complete/:paymentId', element: <CompletePage /> },
          {
            path: 'checkout/kakaopay/success',
            element: <KakaoPayCallbackPage result="success" />,
          },
          {
            path: 'checkout/kakaopay/cancel',
            element: <KakaoPayCallbackPage result="cancel" />,
          },
          {
            path: 'checkout/kakaopay/fail',
            element: <KakaoPayCallbackPage result="fail" />,
          },
        ],
      },
    ],
  },

  /* 판매자센터 */
  {
    path: 'seller',
    element: <RequireAuth />,
    children: [
      // 전환 화면은 전환 전에도 보여야 하므로 가드 밖에 둡니다.
      { path: 'verify', element: <SellerVerifyPage /> },
      {
        element: <RequireSeller />,
        children: [
          {
            element: <SellerLayout />,
            children: [
              { index: true, element: <SellerHomePage /> },
              { path: 'products', element: <SellerProductsPage /> },
              { path: 'products/new', element: <SellerProductNewPage /> },
              { path: 'products/:productId/edit', element: <SellerProductEditPage /> },
              { path: 'group-buys/new', element: <SellerGroupBuyNewPage /> },
              { path: 'group-buys/:groupBuyId', element: <SellerGroupBuyDetailPage /> },
              { path: 'orders', element: <SellerOrdersPage /> },
              { path: 'returns', element: <SellerReturnsPage /> },
              { path: 'settlements', element: <SellerSettlementsPage /> },
            ],
          },
        ],
      },
    ],
  },

  { path: '*', element: <NotFoundPage /> },
])
