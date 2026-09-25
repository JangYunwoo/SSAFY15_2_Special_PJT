/* eslint-disable react-refresh/only-export-components --
   라우터는 컴포넌트가 아니라 라우트 설정(router)을 내보내는 모듈입니다. */
import { lazy } from 'react'
import { createBrowserRouter } from 'react-router'

/* 가드는 세션 스토어만 읽는 몇 줄짜리라 어느 화면에서나 바로 필요합니다. */
import { RequireAdmin, RequireAuth, RequireSeller } from './layouts/guards'
import { RootLayout } from './layouts/RootLayout'

/* 첫 화면 두 개만 즉시 번들에 넣고, 나머지는 라우트 단위로 쪼갭니다. */
import { GroupBuyListPage } from '@/pages/GroupBuyListPage'
import { HomePage } from '@/pages/HomePage'

/*
 * 레이아웃도 페이지와 똑같이 라우트 단위로 쪼갭니다.
 *
 * 정적 import 하면 레이아웃과 그 의존성(AdminLayout → useAdminAuth → adminApi
 * …)이 전부 진입 청크에 들어가, 홈 화면만 보는 사람도 관리자 콘솔 코드를
 * 내려받게 됩니다. App 이 RouterProvider 를 Suspense 로 감싸고 있어 로딩 중에는
 * 페이지와 같은 폴백이 나옵니다.
 */
const AdminLayout = lazy(() =>
  import('./layouts/AdminLayout').then((m) => ({ default: m.AdminLayout })),
)
const SellerLayout = lazy(() =>
  import('./layouts/SellerLayout').then((m) => ({ default: m.SellerLayout })),
)
const CheckoutLayout = lazy(() =>
  import('./layouts/CheckoutLayout').then((m) => ({ default: m.CheckoutLayout })),
)

const AboutPage = lazy(() => import('@/pages/AboutPage'))
const LikesPage = lazy(() => import('@/pages/LikesPage'))
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
const OrderDetailPage = lazy(() => import('@/pages/orders/OrderDetailPage'))
const OrdersPaymentsPage = lazy(() => import('@/pages/orders/OrdersPaymentsPage'))
const ReturnsPage = lazy(() => import('@/pages/orders/ReturnsPage'))
const ConfirmPage = lazy(() => import('@/pages/orders/ConfirmPage'))
const ReturnRequestPage = lazy(() => import('@/pages/orders/ReturnRequestPage'))
const DisputePage = lazy(() => import('@/pages/orders/DisputePage'))

const SellerVerifyPage = lazy(() => import('@/pages/seller/SellerVerifyPage'))
const SellerHomePage = lazy(() => import('@/pages/seller/SellerHomePage'))
const SellerProductsPage = lazy(() => import('@/pages/seller/SellerProductsPage'))
const SellerProductNewPage = lazy(() => import('@/pages/seller/SellerProductNewPage'))
const SellerProductEditPage = lazy(() => import('@/pages/seller/SellerProductEditPage'))
const SellerGroupBuyNewPage = lazy(() => import('@/pages/seller/SellerGroupBuyNewPage'))
const SellerGroupBuyDetailPage = lazy(() => import('@/pages/seller/SellerGroupBuyDetailPage'))
const SellerGroupBuyEditPage = lazy(() => import('@/pages/seller/SellerGroupBuyEditPage'))
const SellerOrdersPage = lazy(() => import('@/pages/seller/SellerOrdersPage'))
const SellerReturnsPage = lazy(() => import('@/pages/seller/SellerReturnsPage'))
const SellerSettlementsPage = lazy(() => import('@/pages/seller/SellerSettlementsPage'))

const AdminLoginPage = lazy(() => import('@/pages/admin/AdminLoginPage'))
const AdminHomePage = lazy(() => import('@/pages/admin/AdminHomePage'))
const AdminSettlementsPage = lazy(() => import('@/pages/admin/AdminSettlementsPage'))
const AdminRefundsPage = lazy(() => import('@/pages/admin/AdminRefundsPage'))
const AdminReturnsPage = lazy(() => import('@/pages/admin/AdminReturnsPage'))
const AdminPenaltiesPage = lazy(() => import('@/pages/admin/AdminPenaltiesPage'))
const AdminProductsPage = lazy(() => import('@/pages/admin/AdminProductsPage'))
const AdminPoliciesPage = lazy(() => import('@/pages/admin/AdminPoliciesPage'))
const AdminLogsPage = lazy(() => import('@/pages/admin/AdminLogsPage'))

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
          { path: 'likes', element: <LikesPage /> },
          { path: 'me', element: <MyPage /> },
          { path: 'orders', element: <OrdersPage /> },
          { path: 'orders/payments', element: <OrdersPaymentsPage /> },
          { path: 'orders/returns', element: <ReturnsPage /> },
          /* 정적 경로(payments·returns)가 먼저 잡히므로 :participationId 와 겹치지 않습니다 */
          { path: 'orders/:participationId', element: <OrderDetailPage /> },
          { path: 'orders/:participationId/confirm', element: <ConfirmPage /> },
          { path: 'orders/:participationId/return', element: <ReturnRequestPage /> },
          { path: 'orders/:participationId/dispute', element: <DisputePage /> },
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
              { path: 'group-buys/:groupBuyId/edit', element: <SellerGroupBuyEditPage /> },
              { path: 'orders', element: <SellerOrdersPage /> },
              { path: 'returns', element: <SellerReturnsPage /> },
              { path: 'settlements', element: <SellerSettlementsPage /> },
            ],
          },
        ],
      },
    ],
  },

  /*
   * 관리자 콘솔.
   *
   * RequireAuth 를 겹치지 않습니다. 관리자는 `admins` 의 별도 계정이라
   * 구매자 로그인 화면으로 보내면 거기서 로그인해도 다시 막힙니다.
   * RequireAdmin 하나가 로그인 여부와 권한을 함께 판단합니다.
   */
  {
    path: 'admin',
    children: [
      { path: 'login', element: <AdminLoginPage /> },
      {
        element: <RequireAdmin />,
        children: [
          {
            element: <AdminLayout />,
            children: [
              { index: true, element: <AdminHomePage /> },
              { path: 'settlements', element: <AdminSettlementsPage /> },
              { path: 'refunds', element: <AdminRefundsPage /> },
              { path: 'returns', element: <AdminReturnsPage /> },
              { path: 'penalties', element: <AdminPenaltiesPage /> },
              { path: 'products', element: <AdminProductsPage /> },
              { path: 'policies', element: <AdminPoliciesPage /> },
              { path: 'logs', element: <AdminLogsPage /> },
            ],
          },
        ],
      },
    ],
  },

  { path: '*', element: <NotFoundPage /> },
])


