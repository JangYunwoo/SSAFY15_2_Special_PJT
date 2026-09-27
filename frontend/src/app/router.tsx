import { lazyRoute } from './lazyRoute'
import { createBrowserRouter } from 'react-router'

/* 가드는 세션 스토어만 읽는 몇 줄짜리라 어느 화면에서나 바로 필요합니다. */
import { RequireAdmin, RequireAuth, RequireSeller } from './layouts/guards'
import { AppShell } from './layouts/AppShell'
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
const AdminLayout = lazyRoute(() =>
  import('./layouts/AdminLayout').then((m) => ({ default: m.AdminLayout })),
)
const SellerLayout = lazyRoute(() =>
  import('./layouts/SellerLayout').then((m) => ({ default: m.SellerLayout })),
)
const CheckoutLayout = lazyRoute(() =>
  import('./layouts/CheckoutLayout').then((m) => ({ default: m.CheckoutLayout })),
)

const AboutPage = lazyRoute(() => import('@/pages/AboutPage'))
const LikesPage = lazyRoute(() => import('@/pages/LikesPage'))
const GroupBuyDetailPage = lazyRoute(() => import('@/pages/GroupBuyDetailPage'))
const LoginPage = lazyRoute(() => import('@/pages/LoginPage'))
const SignupPage = lazyRoute(() => import('@/pages/SignupPage'))
const MyPage = lazyRoute(() => import('@/pages/MyPage'))
const NotFoundPage = lazyRoute(() => import('@/pages/NotFoundPage'))

const ShippingPage = lazyRoute(() => import('@/pages/checkout/ShippingPage'))
const PaymentPage = lazyRoute(() => import('@/pages/checkout/PaymentPage'))
const CompletePage = lazyRoute(() => import('@/pages/checkout/CompletePage'))
const KakaoPayCallbackPage = lazyRoute(() => import('@/pages/checkout/KakaoPayCallbackPage'))

const OrdersPage = lazyRoute(() => import('@/pages/orders/OrdersPage'))
const OrderDetailPage = lazyRoute(() => import('@/pages/orders/OrderDetailPage'))
const ConfirmPage = lazyRoute(() => import('@/pages/orders/ConfirmPage'))
const ReturnRequestPage = lazyRoute(() => import('@/pages/orders/ReturnRequestPage'))
const DisputePage = lazyRoute(() => import('@/pages/orders/DisputePage'))

const SellerVerifyPage = lazyRoute(() => import('@/pages/seller/SellerVerifyPage'))
const SellerHomePage = lazyRoute(() => import('@/pages/seller/SellerHomePage'))
const SellerProductsPage = lazyRoute(() => import('@/pages/seller/SellerProductsPage'))
const SellerProductNewPage = lazyRoute(() => import('@/pages/seller/SellerProductNewPage'))
const SellerProductEditPage = lazyRoute(() => import('@/pages/seller/SellerProductEditPage'))
const SellerGroupBuyNewPage = lazyRoute(() => import('@/pages/seller/SellerGroupBuyNewPage'))
const SellerGroupBuyDetailPage = lazyRoute(() => import('@/pages/seller/SellerGroupBuyDetailPage'))
const SellerGroupBuyEditPage = lazyRoute(() => import('@/pages/seller/SellerGroupBuyEditPage'))
const SellerOrdersPage = lazyRoute(() => import('@/pages/seller/SellerOrdersPage'))
const SellerReturnsPage = lazyRoute(() => import('@/pages/seller/SellerReturnsPage'))
const SellerSettlementsPage = lazyRoute(() => import('@/pages/seller/SellerSettlementsPage'))

const AdminLoginPage = lazyRoute(() => import('@/pages/admin/AdminLoginPage'))
const AdminHomePage = lazyRoute(() => import('@/pages/admin/AdminHomePage'))
const AdminSettlementsPage = lazyRoute(() => import('@/pages/admin/AdminSettlementsPage'))
const AdminRefundsPage = lazyRoute(() => import('@/pages/admin/AdminRefundsPage'))
const AdminReturnsPage = lazyRoute(() => import('@/pages/admin/AdminReturnsPage'))
const AdminPenaltiesPage = lazyRoute(() => import('@/pages/admin/AdminPenaltiesPage'))
const AdminProductsPage = lazyRoute(() => import('@/pages/admin/AdminProductsPage'))
const AdminPoliciesPage = lazyRoute(() => import('@/pages/admin/AdminPoliciesPage'))
const AdminLogsPage = lazyRoute(() => import('@/pages/admin/AdminLogsPage'))

export const router = createBrowserRouter([
  {
    // 스크롤 복원을 모든 화면에 한 번만 겁니다 (AppShell 주석 참고)
    element: <AppShell />,
    children: [
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
              /* confirm·return·dispute 는 한 칸 더 깊어 :participationId 와 겹치지 않습니다 */
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
    ],
  },
])
