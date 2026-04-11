import Vue from 'vue'
import VueRouter from 'vue-router'
import HomeView from '../views/HomeView.vue'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  {
    path: '/checkout',
    name: 'checkout',
    component: () => import('../views/CheckoutView.vue')
  },
  {
    path: '/contact',
    name: 'contact',
    component: () => import('../views/ContactView.vue')
  },
  {
    path: '/admin/orders/:orderId/invoice',
    name: 'admin-order-invoice',
    component: () => import('../views/OrderInvoicePrintView.vue'),
    meta: { minimalChrome: true }
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    children: [
      {
        path: '',
        name: 'admin',
        component: () => import('../views/admin/AdminDashboardView.vue'),
        meta: {
          adminTitle: 'Dashboard',
          adminLead: 'Account overview and quick orientation — switch tabs for products, orders, and settings.'
        }
      },
      {
        path: 'products',
        name: 'admin-products',
        component: () => import('../views/admin/AdminProductsView.vue'),
        meta: {
          adminTitle: 'Products',
          adminLead: 'Publish new items and manage inventory — changes sync to the shop in real time.'
        }
      },
      {
        path: 'orders',
        name: 'admin-orders',
        component: () => import('../views/admin/AdminOrdersView.vue'),
        meta: {
          adminTitle: 'Orders',
          adminLead: 'Review customer orders, confirm payments, update fulfillment, and print invoices.'
        }
      },
      {
        path: 'insights',
        name: 'admin-insights',
        component: () => import('../views/admin/AdminInsightsView.vue'),
        meta: {
          adminTitle: 'Insights',
          adminLead: 'Sales and performance for the period and filters you choose.'
        }
      },
      {
        path: 'store',
        name: 'admin-store',
        component: () => import('../views/admin/AdminStoreSettingsView.vue'),
        meta: {
          adminTitle: 'Store settings',
          adminLead: 'Delivery, banking, branding, and contact details shown to customers.'
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

export default router
