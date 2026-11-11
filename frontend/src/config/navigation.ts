export interface NavItem { label: string; path: string }

export const customerNav: NavItem[] = [
  { label: 'Shop', path: '/shop' }, { label: 'Men', path: '/men' }, { label: 'Women', path: '/women' }, { label: 'Kids', path: '/kids' },
  { label: 'Trending', path: '/trending' }, { label: 'Support', path: '/support' },
]

export const tailorNav: NavItem[] = [
  { label: 'Dashboard', path: '/tailor' }, { label: 'Available orders', path: '/tailor/available' },
  { label: 'Assigned orders', path: '/tailor/assigned' }, { label: 'Completed', path: '/tailor/completed' },
  { label: 'Earnings', path: '/tailor/earnings' }, { label: 'Reports', path: '/tailor/reports' },
  { label: 'Notifications', path: '/tailor/notifications' }, { label: 'Profile', path: '/tailor/profile' },
]

/** `permission` is checked against backend permission codes from Phase 2; the backend remains the authority. */
export const adminNav: (NavItem & { permission?: string })[] = [
  { label: 'Dashboard', path: '/admin' },
  { label: 'Users', path: '/admin/users', permission: 'USER_VIEW' },
  { label: 'Admins', path: '/admin/admins', permission: 'ADMIN_MANAGE' },
  { label: 'Tailors', path: '/admin/tailor-profiles', permission: 'USER_VIEW' },
  { label: 'Tailor approvals', path: '/admin/tailors/pending', permission: 'TAILOR_APPLICATION_REVIEW' },
  { label: 'Tailor categories', path: '/admin/tailor-categories', permission: 'TAILOR_CATEGORY_MANAGE' },
  { label: 'Roles & permissions', path: '/admin/roles', permission: 'ROLE_MANAGE' },
  { label: 'Products', path: '/admin/products', permission: 'PRODUCT_VIEW' },
  { label: 'Categories', path: '/admin/categories', permission: 'CATEGORY_MANAGE' },
  { label: 'Trend collections', path: '/admin/collections', permission: 'PRODUCT_VIEW' },
  { label: 'Fabrics, colours & sizes', path: '/admin/attributes', permission: 'FABRIC_MANAGE' },
  { label: 'Customization', path: '/admin/customization', permission: 'CUSTOMIZATION_MANAGE' },
  { label: 'Orders', path: '/admin/orders', permission: 'ORDER_VIEW' },
  { label: 'Coupons', path: '/admin/coupons', permission: 'COUPON_MANAGE' },
  { label: 'Payments', path: '/admin/payments', permission: 'PAYMENT_VIEW' },
  { label: 'Refunds', path: '/admin/refunds', permission: 'PAYMENT_VIEW' },
  { label: 'Earnings & payouts', path: '/admin/earnings', permission: 'EARNINGS_MANAGE' },
  { label: 'Reports', path: '/admin/reports', permission: 'REPORT_VIEW' },
  { label: 'Logs', path: '/admin/logs', permission: 'LOG_VIEW' },
  { label: 'Website settings', path: '/admin/settings', permission: 'SETTINGS_MANAGE' },
]
