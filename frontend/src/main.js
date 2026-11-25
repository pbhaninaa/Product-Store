import Vue from 'vue'
import App from './App.vue'
import './styles/workspace.css'
import './views/admin/admin-common.css'
import router from './router'
import vuetify from './plugins/vuetify'
import { logout } from './services/auth'
import { SESSION_EXPIRED_EVENT } from './services/sessionExpiry'

Vue.config.productionTip = false

// Expired/revoked token: clear the session, then send clients to sign in again. Merchant admin
// and support screens already swap to their sign-in form when the session is cleared.
window.addEventListener(SESSION_EXPIRED_EVENT, async () => {
  await logout()
  const current = router.currentRoute
  if (current.matched.some((r) => r.meta && r.meta.requiresSupportConsole)) {
    router.replace({ path: '/m/platform/admin', query: { support: '1', expired: '1' } }).catch(() => {})
  } else if (current.matched.some((r) => r.meta && r.meta.requiresClient)) {
    router.replace({ name: 'client-login', query: { next: current.fullPath, expired: '1' } }).catch(() => {})
  }
})

new Vue({
  router,
  vuetify,
  render: h => h(App)
}).$mount('#app')
