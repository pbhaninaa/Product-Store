import Vue from 'vue'
import VueRouter from 'vue-router'
import HomeView from '../views/HomeView.vue'
import * as auth from '@/services/auth'
import { isPlatformAdminUser } from '@/views/support/supportNav'

function isMerchantStaffUser(u) {
  if (!u || !Array.isArray(u.roles)) return false
  return u.roles.includes('MERCHANT_OWNER') || u.roles.includes('MERCHANT_STAFF')
}

Vue.use(VueRouter)

function canAccessSupportConsole() {
  const u = auth.getSessionUser()
  if (!u || !Array.isArray(u.roles)) return false
  return u.roles.includes('SUPPORT_USER') || u.roles.includes('PLATFORM_ADMIN')
}

function clientHomeNext(u) {
  if (auth.isClientUser(u)) return { name: 'client-hub' }
  if (isMerchantStaffUser(u)) {
    const slug = u && typeof u.tenant === 'string' ? u.tenant.trim() : ''
    return slug ? `/m/${encodeURIComponent(slug)}/admin` : '/signup'
  }
  if (auth.isSupportOrPlatformOnlyUser(u)) return { name: 'support-dashboard' }
  return { name: 'client-login', query: { next: '/hub' } }
}

/** Opening the app lands on the provider list; signed-in merchants and support go to their workspace. */
function landingFor(u) {
  if (isMerchantStaffUser(u) || auth.isSupportOrPlatformOnlyUser(u)) return clientHomeNext(u)
  return { name: 'provider-directory' }
}

const routes = [
  {
    path: '/',
    name: 'landing',
    redirect: () => landingFor(auth.getSessionUser())
  },
  {
    path: '/hub',
    name: 'client-hub',
    component: () => import('../views/ClientHubView.vue'),
    meta: { requiresClient: true }
  },
  {
    path: '/discover',
    name: 'client-discover',
    component: () => import('../views/ClientDiscoverView.vue'),
    meta: { requiresClient: true }
  },
  {
    // Shareable link for people without a provider's invite: every shop and salon, no sign-in needed.
    path: '/providers',
    name: 'provider-directory',
    component: () => import('../views/ProviderDirectoryView.vue')
  },
  {
    path: '/signup',
    name: 'merchant-signup',
    component: () => import('../views/RegisterView.vue')
  },
  {
    path: '/account/login',
    name: 'client-login',
    component: () => import('../views/ClientAccountView.vue')
  },
  {
    path: '/account/register',
    name: 'client-register',
    component: () => import('../views/RegisterView.vue')
  },
  {
    path: '/account/history',
    name: 'client-history',
    component: () => import('../views/ClientHistoryView.vue'),
    meta: { requiresClient: true }
  },
  {
    path: '/payment/return',
    name: 'payment-return',
    component: () => import('../views/PaymentReturnView.vue')
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('../views/ForgotPasswordView.vue')
  },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('../views/ResetPasswordView.vue')
  },
  {
    path: '/support',
    component: () => import('../views/support/SupportShell.vue'),
    meta: { requiresSupportConsole: true },
    children: [
      {
        path: '',
        name: 'support-dashboard',
        component: () => import('../views/support/SupportDashboardView.vue')
      },
      {
        path: 'merchants',
        name: 'support-merchants',
        component: () => import('../views/support/SupportMerchantsView.vue')
      },
      {
        path: 'subscriptions',
        name: 'support-subscriptions',
        component: () => import('../views/support/SupportSubscriptionsView.vue')
      },
      {
        path: 'orders',
        name: 'support-orders',
        component: () => import('../views/support/SupportOrdersView.vue')
      },
      {
        path: 'bookings',
        name: 'support-bookings',
        component: () => import('../views/support/SupportBookingsView.vue')
      },
      {
        path: 'tickets',
        name: 'support-tickets',
        component: () => import('../views/support/SupportTicketsView.vue')
      },
      {
        path: 'shadow',
        name: 'support-shadow',
        component: () => import('../views/support/SupportShadowView.vue')
      },
      {
        path: 'notifications',
        name: 'support-notifications',
        component: () => import('../views/support/SupportNotificationsView.vue')
      },
      {
        path: 'features',
        name: 'support-features',
        component: () => import('../views/support/SupportFeaturesView.vue')
      },
      {
        path: 'audit',
        name: 'support-audit',
        component: () => import('../views/support/SupportAuditView.vue')
      },
      {
        path: 'help-contact',
        name: 'support-help-contact',
        component: () => import('../views/support/SupportHelpContactView.vue')
      },
      {
        path: 'users',
        name: 'support-users',
        component: () => import('../views/support/SupportUsersView.vue')
      },
      {
        path: 'staff',
        name: 'support-staff',
        component: () => import('../views/support/SupportStaffView.vue')
      },
      {
        path: 'payouts',
        name: 'support-payouts',
        component: () => import('../views/support/SupportPayoutsView.vue'),
        meta: { platformAdminOnly: true }
      },
      {
        path: 'payouts/:tenantId/release',
        name: 'support-payout-release',
        component: () => import('../views/support/SupportPayoutReleaseView.vue'),
        meta: { platformAdminOnly: true }
      },
      {
        path: 'payout-history',
        name: 'support-payout-history',
        component: () => import('../views/support/SupportPayoutHistoryView.vue'),
        meta: { platformAdminOnly: true }
      },
      {
        path: 'referrals',
        name: 'support-referrals',
        component: () => import('../views/support/SupportReferralsView.vue')
      },
      {
        path: 'account',
        name: 'support-account',
        component: () => import('../views/support/SupportAccountView.vue')
      }
    ]
  },
  {
    path: '/m/:merchantSlug',
    name: 'merchant-home',
    component: HomeView
  },
  {
    path: '/m/:merchantSlug/track',
    name: 'merchant-track',
    component: () => import('../views/TrackView.vue')
  },
  {
    path: '/m/:merchantSlug/help',
    name: 'merchant-help',
    component: () => import('../views/HelpCenterView.vue')
  },
  {
    path: '/m/:merchantSlug/checkout',
    name: 'merchant-checkout',
    component: () => import('../views/CheckoutView.vue')
  },
  {
    // Promotions and salon services now live on the store page under its filters; old links land there.
    path: '/m/:merchantSlug/promotions',
    name: 'merchant-promotions',
    redirect: (to) => ({ name: 'merchant-home', params: to.params, query: { ...to.query, show: 'promotions' } })
  },
  {
    path: '/m/:merchantSlug/payment/return',
    name: 'merchant-payment-return',
    component: () => import('../views/PaymentReturnView.vue')
  },
  {
    path: '/m/:merchantSlug/contact',
    name: 'merchant-contact',
    component: () => import('../views/ContactView.vue')
  },
  {
    path: '/m/:merchantSlug/salon/services',
    name: 'merchant-salon-services',
    redirect: (to) => ({ name: 'merchant-home', params: to.params, query: { ...to.query, show: 'services' } })
  },
  {
    path: '/m/:merchantSlug/salon/book/:serviceId',
    name: 'merchant-salon-book',
    component: () => import('../views/SalonBookView.vue')
  },
  {
    path: '/m/:merchantSlug/admin/orders/:orderId/invoice',
    name: 'merchant-admin-order-invoice',
    component: () => import('../views/OrderInvoicePrintView.vue'),
    meta: { minimalChrome: true }
  },
  {
    path: '/m/:merchantSlug/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    children: [
      {
        path: '',
        name: 'merchant-admin',
        component: () => import('../views/admin/AdminDashboardView.vue'),
        meta: {
          adminTitle: 'Dashboard',
          adminLead: 'Account overview and quick orientation. Switch tabs for products, orders, and settings.'
        }
      },
      {
        path: 'products',
        name: 'merchant-admin-products',
        component: () => import('../views/admin/AdminProductsView.vue'),
        meta: {
          adminTitle: 'Products',
          adminLead: 'Publish new items and manage inventory. Changes sync to your store in real time.'
        }
      },
      {
        path: 'promotions',
        name: 'merchant-admin-promotions',
        component: () => import('../views/admin/AdminPromotionsView.vue'),
        meta: {
          adminTitle: 'Promotions',
          adminLead: 'Percentage or fixed discounts applied at checkout and on bookings.'
        }
      },
      {
        path: 'orders',
        name: 'merchant-admin-orders',
        component: () => import('../views/admin/AdminOrdersView.vue'),
        meta: {
          adminTitle: 'Orders',
          adminLead: 'Review customer orders, confirm payments, update fulfilment (processing → ready → completed), and print invoices.'
        }
      },
      {
        path: 'insights',
        name: 'merchant-admin-insights',
        component: () => import('../views/admin/AdminInsightsView.vue'),
        meta: {
          adminTitle: 'Insights',
          adminLead: 'Sales and performance for the period and filters you choose.'
        }
      },
      {
        path: 'store',
        name: 'merchant-admin-store',
        component: () => import('../views/admin/AdminStoreSettingsView.vue'),
        meta: {
          adminTitle: 'Store settings',
          adminLead:
            'Delivery, payout details, branding (name, business type, images), and contact details shown to customers.'
        }
      },
      {
        path: 'salon/staff',
        name: 'merchant-admin-salon-staff',
        component: () => import('../views/admin/AdminSalonStaffView.vue'),
        meta: {
          adminTitle: 'Staff management',
          adminLead: 'Add team members, activation, and weekly bookable hours for bookings.'
        }
      },
      {
        path: 'salon/payments',
        name: 'merchant-admin-salon-payments',
        component: () => import('../views/admin/AdminSalonPaymentsView.vue'),
        meta: {
          adminTitle: 'Payments',
          adminLead: 'Every order and booking payment: received, awaiting and cancelled.'
        }
      },
      {
        path: 'salon/bookings',
        name: 'merchant-admin-salon-bookings',
        component: () => import('../views/admin/AdminSalonBookingsView.vue'),
        meta: {
          adminTitle: 'Bookings',
          adminLead: 'Appointments from your public booking flow. Confirm payment, start the service, then complete it (customers can review after completion).'
        }
      },
      {
        path: 'salon',
        name: 'merchant-admin-salon',
        component: () => import('../views/admin/AdminSalonView.vue'),
        meta: {
          adminTitle: 'Services',
          adminLead: 'Publish and edit bookable services (when business type includes Services).'
        }
      },
      {
        path: 'team',
        name: 'merchant-admin-team',
        component: () => import('../views/admin/AdminTeamHubView.vue'),
        meta: {
          adminTitle: 'Team & Payroll',
          adminLead:
            'Give team members their own sign in, work out what each is owed in payroll, and see your expected income.'
        }
      },
      {
        path: 'team/payroll',
        redirect: to => ({
          name: 'merchant-admin-team',
          params: to.params,
          query: { tab: 'payroll' }
        })
      },
      {
        path: 'my-income',
        redirect: to => ({
          name: 'merchant-admin-team',
          params: to.params,
          query: { tab: 'income' }
        })
      },
      {
        path: 'settings',
        name: 'merchant-admin-settings',
        component: () => import('../views/admin/AdminSettingsView.vue'),
        meta: {
          adminTitle: 'Settings',
          adminLead: 'Catalogue, team, store details, plan, and help in one place.'
        }
      },
      {
        path: 'notifications',
        name: 'merchant-admin-notifications',
        component: () => import('../views/admin/AdminNotificationsView.vue'),
        meta: {
          adminTitle: 'Notifications',
          adminLead: 'In app alerts for new orders, bookings, and payroll.'
        }
      },
      {
        path: 'subscription',
        name: 'merchant-admin-subscription',
        component: () => import('../views/admin/AdminSubscriptionView.vue'),
        meta: {
          adminTitle: 'Plan & billing',
          adminLead:
            '7 day Free Trial from signup with full access. After expiry, renew securely with Paystack by card.'
        }
      },
      {
        path: 'help',
        name: 'merchant-admin-help',
        component: () => import('../views/admin/AdminHelpView.vue'),
        meta: {
          adminTitle: 'Help',
          adminLead: 'Contact platform support or open a ticket for your store.'
        }
      }
    ]
  }
]

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes,
  // New pages open at the top; back/forward restores the previous position; #anchors scroll to the target.
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { selector: to.hash }
    if (to.path === from.path) return false
    return { x: 0, y: 0 }
  }
})

router.beforeEach((to, _from, next) => {
  if (to.matched.some((r) => r.meta && r.meta.requiresSupportConsole)) {
    if (!canAccessSupportConsole()) {
      auth.logout()
      next({ path: '/m/platform/admin', query: { support: '1' } })
      return
    }
    // Payouts and commission are platform admin pages; support staff go back to the console home.
    // UX only: the API answers 403 to them anyway.
    if (to.matched.some((r) => r.meta && r.meta.platformAdminOnly) && !isPlatformAdminUser(auth.getSessionUser())) {
      next({ name: 'support-dashboard', replace: true })
      return
    }
  }

  const u = auth.getSessionUser()
  if (to.matched.some((r) => r.meta && r.meta.requiresClient)) {
    if (!auth.isClientUser(u)) {
      if (isMerchantStaffUser(u) || auth.isSupportOrPlatformOnlyUser(u)) {
        next(clientHomeNext(u))
        return
      }
      next({ name: 'client-login', query: { next: to.fullPath } })
      return
    }
  }
  if (auth.isClientUser(u) && to.path.includes('/admin')) {
    next({ path: '/', replace: true })
    return
  }
  if (auth.isSupportOrPlatformOnlyUser(u) && to.path.includes('/admin') && !auth.isShadowSession()) {
    next({ name: 'support-dashboard', replace: true })
    return
  }

  const tenantSlug = u && typeof u.tenant === 'string' ? u.tenant.trim() : ''
  const slugInPath =
    to.params.merchantSlug != null ? String(to.params.merchantSlug).trim() : ''
  if (
    isMerchantStaffUser(u) &&
    tenantSlug &&
    slugInPath &&
    slugInPath !== tenantSlug &&
    to.fullPath.includes('/admin')
  ) {
    const path = to.path.replace(/^\/m\/[^/]+/, `/m/${encodeURIComponent(tenantSlug)}`)
    next({ path, query: to.query, hash: to.hash, replace: true })
    return
  }

  next()
})

export default router
