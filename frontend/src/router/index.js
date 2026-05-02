import Vue from 'vue'
import VueRouter from 'vue-router'
import HomeView from '../views/HomeView.vue'
import * as auth from '@/services/auth'

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

const routes = [
  {
    path: '/',
    redirect: '/m/demo'
  },
  {
    path: '/signup',
    name: 'merchant-signup',
    component: () => import('../views/MerchantSignupView.vue')
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
      }
    ]
  },
  {
    path: '/m/:merchantSlug',
    name: 'merchant-home',
    component: HomeView
  },
  {
    path: '/m/:merchantSlug/checkout',
    name: 'merchant-checkout',
    component: () => import('../views/CheckoutView.vue')
  },
  {
    path: '/m/:merchantSlug/contact',
    name: 'merchant-contact',
    component: () => import('../views/ContactView.vue')
  },
  {
    path: '/m/:merchantSlug/salon/services',
    name: 'merchant-salon-services',
    component: () => import('../views/SalonServicesView.vue')
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
          adminLead: 'Account overview and quick orientation — switch tabs for products, orders, and settings.'
        }
      },
      {
        path: 'products',
        name: 'merchant-admin-products',
        component: () => import('../views/admin/AdminProductsView.vue'),
        meta: {
          adminTitle: 'Products',
          adminLead: 'Publish new items and manage inventory — changes sync to the shop in real time.'
        }
      },
      {
        path: 'orders',
        name: 'merchant-admin-orders',
        component: () => import('../views/admin/AdminOrdersView.vue'),
        meta: {
          adminTitle: 'Orders',
          adminLead: 'Review customer orders, confirm payments, update fulfillment, and print invoices.'
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
            'Delivery, banking, branding (name, store type, images), and contact details shown to customers.'
        }
      },
      {
        path: 'salon/staff',
        name: 'merchant-admin-salon-staff',
        component: () => import('../views/admin/AdminSalonStaffView.vue'),
        meta: {
          adminTitle: 'Staff management',
          adminLead: 'Add team members, activation, and weekly bookable hours for salon booking.'
        }
      },
      {
        path: 'salon/payments',
        name: 'merchant-admin-salon-payments',
        component: () => import('../views/admin/AdminSalonPaymentsView.vue'),
        meta: {
          adminTitle: 'Payments',
          adminLead: 'Recent orders and payment-related activity for your salon checkout.'
        }
      },
      {
        path: 'salon/bookings',
        name: 'merchant-admin-salon-bookings',
        component: () => import('../views/admin/AdminSalonBookingsView.vue'),
        meta: {
          adminTitle: 'Salon bookings',
          adminLead: 'Appointments from your public booking flow — confirm or cancel as you run the day.'
        }
      },
      {
        path: 'salon',
        name: 'merchant-admin-salon',
        component: () => import('../views/admin/AdminSalonView.vue'),
        meta: {
          adminTitle: 'Salon services',
          adminLead: 'Publish and edit bookable services (when business type is Salon).'
        }
      }
    ]
  }
]

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes
})

router.beforeEach((to, _from, next) => {
  if (to.matched.some((r) => r.meta && r.meta.requiresSupportConsole)) {
    if (!canAccessSupportConsole()) {
      auth.logout()
      next({ name: 'merchant-home', params: { merchantSlug: 'demo' }, query: { support: '1' } })
      return
    }
  }

  const u = auth.getSessionUser()
  if (auth.isSupportOrPlatformOnlyUser(u) && to.path.includes('/admin')) {
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
