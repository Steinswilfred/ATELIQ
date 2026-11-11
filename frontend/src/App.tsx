import { lazy, Suspense, useEffect } from 'react'
import { MotionConfig } from 'framer-motion'
import { Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './components/auth/ProtectedRoute'
import { Toaster } from './components/ui/Toaster'
import { adminNav, tailorNav } from './config/navigation'
import { CustomerLayout } from './layouts/CustomerLayout'
import { DashboardLayout } from './layouts/DashboardLayout'
import Account from './pages/Account'
import AccountLayout from './pages/account/AccountLayout'
import Addresses from './pages/account/Addresses'
import Designs from './pages/account/Designs'
import OrderDetail from './pages/account/OrderDetail'
import Orders from './pages/account/Orders'
import Cart from './pages/checkout/Cart'
import Checkout from './pages/checkout/Checkout'
import PaymentResult from './pages/checkout/PaymentResult'
import Measurements from './pages/account/Measurements'
import Listing from './pages/shop/Listing'
import ProductPage from './pages/shop/ProductPage'
import Trending from './pages/shop/Trending'
import ForgotPassword from './pages/auth/ForgotPassword'
import Login from './pages/auth/Login'
import Register from './pages/auth/Register'
import RegisterTailor from './pages/auth/RegisterTailor'
import ResetPassword from './pages/auth/ResetPassword'
import VerifyEmail from './pages/auth/VerifyEmail'
import VerifyPhone from './pages/auth/VerifyPhone'
import Home from './pages/Home'
import Placeholder from './pages/Placeholder'
import { useAuth } from './store/authStore'
import { useCart } from './store/cartStore'
import { useSite } from './store/siteStore'

const AdminAdmins = lazy(() => import('./pages/admin/AdminAdmins'))
const AdminAttributes = lazy(() => import('./pages/admin/AdminAttributes'))
const AdminTailorCategories = lazy(() => import('./pages/admin/AdminTailorCategories'))
const AdminTailors = lazy(() => import('./pages/admin/AdminTailors'))
const AdminTailorDetail = lazy(() => import('./pages/admin/AdminTailorDetail'))
const TailorDashboard = lazy(() => import('./pages/tailor/TailorDashboard'))
const TailorProfile = lazy(() => import('./pages/tailor/TailorProfile'))
const AvailableOrders = lazy(() => import('./pages/tailor/AvailableOrders'))
const AssignedOrders = lazy(() => import('./pages/tailor/AssignedOrders'))
const TailorOrderDetail = lazy(() => import('./pages/tailor/TailorOrderDetail'))
const AdminRefunds = lazy(() => import('./pages/finance/AdminRefunds'))
const AdminEarnings = lazy(() => import('./pages/finance/AdminEarnings'))
const AdminFinanceReport = lazy(() => import('./pages/finance/AdminFinanceReport'))
const TailorEarnings = lazy(() => import('./pages/finance/TailorEarnings'))
const AdminCoupons = lazy(() => import('./pages/admin/AdminCoupons'))
const AdminOrderList = lazy(() => import('./pages/admin/AdminOrders').then((m) => ({ default: m.AdminOrderList })))
const AdminOrderDetail = lazy(() => import('./pages/admin/AdminOrders').then((m) => ({ default: m.AdminOrderDetail })))
const AdminPaymentList = lazy(() => import('./pages/admin/AdminPayments').then((m) => ({ default: m.AdminPaymentList })))
const AdminPaymentDetail = lazy(() => import('./pages/admin/AdminPayments').then((m) => ({ default: m.AdminPaymentDetail })))
const AdminCollections = lazy(() => import('./pages/admin/AdminCollections'))
const AdminCategories = lazy(() => import('./pages/admin/AdminCategories'))
const AdminCustomization = lazy(() => import('./pages/admin/AdminCustomization'))
const AdminProductEditor = lazy(() => import('./pages/admin/AdminProductEditor'))
const AdminProducts = lazy(() => import('./pages/admin/AdminProducts'))
const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'))
const AdminRoles = lazy(() => import('./pages/admin/AdminRoles'))
const AdminSettings = lazy(() => import('./pages/admin/AdminSettings'))
const AdminUserDetail = lazy(() => import('./pages/admin/AdminUserDetail'))
const AdminUsers = lazy(() => import('./pages/admin/AdminUsers'))
const TailorApplicationDetail = lazy(() => import('./pages/admin/TailorApplicationDetail'))
const TailorApplications = lazy(() => import('./pages/admin/TailorApplications'))

export default function App() {
  const bootstrap = useAuth((s) => s.bootstrap)
  const loadSite = useSite((s) => s.load)
  useEffect(() => { void bootstrap(); void loadSite() }, [bootstrap, loadSite])
  const user = useAuth((s) => s.user)
  const refreshCart = useCart((s) => s.refresh); const resetCart = useCart((s) => s.reset)
  useEffect(() => { if (user?.userType === 'CUSTOMER') void refreshCart(); else resetCart() }, [user, refreshCart, resetCart])

  return (
    <MotionConfig reducedMotion="user">
      <Suspense fallback={<div className="p-8 text-sm" role="status">Loading…</div>}>
      <Routes>
        <Route element={<CustomerLayout />}>
          <Route index element={<Home />} />
          <Route path="login" element={<Login />} />
          <Route path="register" element={<Register />} />
          <Route path="tailor/register" element={<RegisterTailor />} />
          <Route path="verify-email" element={<VerifyEmail />} />
          <Route path="verify-phone" element={<VerifyPhone />} />
          <Route path="forgot-password" element={<ForgotPassword />} />
          <Route path="reset-password" element={<ResetPassword />} />
          <Route path="shop" element={<Listing />} />
          <Route path="men" element={<Listing section="men" />} />
          <Route path="women" element={<Listing section="women" />} />
          <Route path="kids" element={<Listing section="kids" />} />
          <Route path="trending" element={<Trending />} />
          <Route path="product/:slug" element={<ProductPage />} />
          <Route path="customize/:slug" element={<ProductPage />} />
          <Route element={<ProtectedRoute types={['CUSTOMER']} />}>
            <Route path="cart" element={<Cart />} />
            <Route path="checkout" element={<Checkout />} />
            <Route path="checkout/result/:orderId" element={<PaymentResult />} />
            <Route path="account" element={<AccountLayout />}>
              <Route index element={<Account />} />
              <Route path="orders" element={<Orders />} />
              <Route path="orders/:id" element={<OrderDetail />} />
              <Route path="addresses" element={<Addresses />} />
              <Route path="designs" element={<Designs />} />
              <Route path="measurements" element={<Measurements />} />
              <Route path="*" element={<Account />} />
            </Route>
          </Route>
          <Route path="*" element={<Placeholder />} />
        </Route>

        <Route element={<ProtectedRoute types={['TAILOR']} />}>
          <Route path="/tailor" element={<DashboardLayout title="Tailor" items={tailorNav} />}>
            <Route index element={<TailorDashboard />} />
            <Route path="profile" element={<TailorProfile />} />
            <Route path="earnings" element={<TailorEarnings />} />
            <Route path="available" element={<AvailableOrders />} />
            <Route path="assigned" element={<AssignedOrders />} />
            <Route path="assigned/:id" element={<TailorOrderDetail />} />
            <Route path="completed" element={<AssignedOrders scope="completed" />} />
            <Route path="*" element={<Placeholder />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute types={['ADMIN', 'SUPER_ADMIN']} />}>
          <Route path="/admin" element={<DashboardLayout title="Admin" items={adminNav} />}>
            <Route index element={<AdminDashboard />} />
            <Route element={<ProtectedRoute permission="USER_VIEW" />}>
              <Route path="users" element={<AdminUsers />} />
              <Route path="users/:id" element={<AdminUserDetail />} />
            </Route>
            <Route element={<ProtectedRoute permission="ADMIN_MANAGE" />}><Route path="admins" element={<AdminAdmins />} /></Route>
            <Route element={<ProtectedRoute permission="USER_VIEW" />}>
              <Route path="tailor-profiles" element={<AdminTailors />} />
              <Route path="tailor-profiles/:userId" element={<AdminTailorDetail />} />
            </Route>
            <Route element={<ProtectedRoute permission="TAILOR_CATEGORY_MANAGE" />}><Route path="tailor-categories" element={<AdminTailorCategories />} /></Route>
            <Route element={<ProtectedRoute permission="TAILOR_APPLICATION_REVIEW" />}>
              <Route path="tailors/pending" element={<TailorApplications />} />
              <Route path="tailors/:id" element={<TailorApplicationDetail />} />
            </Route>
            <Route element={<ProtectedRoute permission="ROLE_MANAGE" />}><Route path="roles" element={<AdminRoles />} /></Route>
            <Route element={<ProtectedRoute permission="PRODUCT_VIEW" />}>
              <Route path="products" element={<AdminProducts />} />
              <Route path="products/:id" element={<AdminProductEditor />} />
            </Route>
            <Route element={<ProtectedRoute permission="ORDER_VIEW" />}><Route path="orders" element={<AdminOrderList />} /><Route path="orders/:id" element={<AdminOrderDetail />} /></Route>
            <Route element={<ProtectedRoute permission="PAYMENT_VIEW" />}><Route path="payments" element={<AdminPaymentList />} /><Route path="payments/:id" element={<AdminPaymentDetail />} /></Route>
            <Route element={<ProtectedRoute permission="PAYMENT_VIEW" />}><Route path="refunds" element={<AdminRefunds />} /></Route>
            <Route element={<ProtectedRoute permission="EARNINGS_MANAGE" />}><Route path="earnings" element={<AdminEarnings />} /></Route>
            <Route element={<ProtectedRoute permission="REPORT_VIEW" />}><Route path="reports" element={<AdminFinanceReport />} /></Route>
            <Route element={<ProtectedRoute permission="COUPON_MANAGE" />}><Route path="coupons" element={<AdminCoupons />} /></Route>
            <Route element={<ProtectedRoute permission="CATEGORY_MANAGE" />}><Route path="categories" element={<AdminCategories />} /></Route>
            <Route element={<ProtectedRoute permission="PRODUCT_VIEW" />}><Route path="collections" element={<AdminCollections />} /></Route>
            <Route element={<ProtectedRoute permission="FABRIC_MANAGE" />}><Route path="attributes" element={<AdminAttributes />} /></Route>
            <Route element={<ProtectedRoute permission="CUSTOMIZATION_MANAGE" />}><Route path="customization" element={<AdminCustomization />} /></Route>
            <Route element={<ProtectedRoute permission="SETTINGS_MANAGE" />}><Route path="settings" element={<AdminSettings />} /></Route>
            <Route path="*" element={<Placeholder />} />
          </Route>
        </Route>
      </Routes>
      </Suspense>
      <Toaster />
    </MotionConfig>
  )
}
