import { createBrowserRouter, Navigate } from 'react-router-dom';
import { PublicLayout } from '../layouts/PublicLayout';
import { DashboardLayout, type DashboardNavItem } from '../layouts/DashboardLayout';
import { Home } from '../pages/Home';
import { NotFound } from '../pages/NotFound';
import { PlaceholderPage } from '../pages/PlaceholderPage';

interface Stub { path: string; title: string; phase: number }
const page = (s: Stub) => ({ path: s.path, element: <PlaceholderPage title={s.title} phase={s.phase} /> });

const publicStubs: Stub[] = [
  { path: 'men', title: 'Men', phase: 5 },
  { path: 'women', title: 'Women', phase: 5 },
  { path: 'kids', title: 'Kids', phase: 5 },
  { path: 'trending', title: 'Trending collections', phase: 5 },
  { path: 'products', title: 'Products', phase: 5 },
  { path: 'products/:id', title: 'Product details', phase: 5 },
  { path: 'customize/:productId', title: 'Customize your clothing', phase: 6 },
  { path: 'cart', title: 'Cart', phase: 8 },
  { path: 'checkout', title: 'Checkout', phase: 8 },
  { path: 'login', title: 'Sign in', phase: 2 },
  { path: 'register', title: 'Create account', phase: 2 },
  { path: 'tailor/register', title: 'Tailor registration', phase: 2 },
  { path: 'tailor/application-status', title: 'Application status', phase: 2 },
  { path: 'account/orders', title: 'My orders', phase: 9 },
  { path: 'account/designs', title: 'Saved designs', phase: 5 },
  { path: 'account/measurements', title: 'Saved measurements', phase: 5 },
  { path: 'account/profile', title: 'My profile', phase: 2 },
  { path: 'account/notifications', title: 'Notifications', phase: 13 },
];

const tailorNav: DashboardNavItem[] = [
  { to: '/tailor/dashboard', label: 'Dashboard' },
  { to: '/tailor/available', label: 'Available orders' },
  { to: '/tailor/assigned', label: 'Assigned orders' },
  { to: '/tailor/completed', label: 'Completed orders' },
  { to: '/tailor/earnings', label: 'Earnings' },
  { to: '/tailor/reports', label: 'Reports' },
  { to: '/tailor/profile', label: 'Profile settings' },
];
const tailorStubs: Stub[] = [
  { path: 'dashboard', title: 'Tailor dashboard', phase: 7 },
  { path: 'available', title: 'Available orders', phase: 9 },
  { path: 'assigned', title: 'Assigned orders', phase: 9 },
  { path: 'completed', title: 'Completed orders', phase: 11 },
  { path: 'earnings', title: 'Earnings', phase: 12 },
  { path: 'reports', title: 'Reports', phase: 13 },
  { path: 'profile', title: 'Profile settings', phase: 7 },
];

const adminNav: DashboardNavItem[] = [
  { to: '/admin', label: 'Dashboard' },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/admins', label: 'Admin accounts' },
  { to: '/admin/tailors', label: 'Tailors' },
  { to: '/admin/tailors/approvals', label: 'Pending approvals' },
  { to: '/admin/roles', label: 'Roles and permissions' },
  { to: '/admin/products', label: 'Products' },
  { to: '/admin/customization', label: 'Customization' },
  { to: '/admin/orders', label: 'Orders' },
  { to: '/admin/payments', label: 'Payments' },
  { to: '/admin/reports', label: 'Reports' },
  { to: '/admin/logs', label: 'Logs' },
  { to: '/admin/settings', label: 'Website settings' },
];
const adminStubs: Stub[] = [
  { path: 'users', title: 'User management', phase: 3 },
  { path: 'admins', title: 'Admin accounts', phase: 3 },
  { path: 'tailors', title: 'Tailor management', phase: 7 },
  { path: 'tailors/approvals', title: 'Pending tailor approvals', phase: 3 },
  { path: 'roles', title: 'Roles and permissions', phase: 3 },
  { path: 'products', title: 'Product management', phase: 4 },
  { path: 'customization', title: 'Customization management', phase: 4 },
  { path: 'orders', title: 'Order management', phase: 9 },
  { path: 'payments', title: 'Payments', phase: 8 },
  { path: 'reports', title: 'Reports and analytics', phase: 13 },
  { path: 'logs', title: 'Activity and security logs', phase: 13 },
  { path: 'settings', title: 'Website settings', phase: 3 },
];

// Phase 2 wraps the /tailor and /admin trees in a ProtectedRoute (role + permission checks).
export const router = createBrowserRouter([
  { path: '/', element: <PublicLayout />, children: [{ index: true, element: <Home /> }, ...publicStubs.map(page), { path: '*', element: <NotFound /> }] },
  {
    path: '/tailor',
    element: <DashboardLayout title="Tailor portal" items={tailorNav} />,
    children: [{ index: true, element: <Navigate to="dashboard" replace /> }, ...tailorStubs.filter((s) => s.path !== 'register').map(page)],
  },
  {
    path: '/admin',
    element: <DashboardLayout title="Admin" items={adminNav} />,
    children: [{ index: true, element: <PlaceholderPage title="Admin dashboard" phase={3} /> }, ...adminStubs.map(page)],
  },
]);
