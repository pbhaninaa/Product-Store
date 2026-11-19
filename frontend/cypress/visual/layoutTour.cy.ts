/**
 * Layout tour: full-page screenshots of the main screens for every role at desktop and phone width,
 * for reviewing alignment (equal card heights/widths) and empty space. Needs the SIT stack running.
 * Run: npx cypress run --config specPattern=cypress/visual/**\/*.cy.ts --spec cypress/visual/layoutTour.cy.ts
 */
const API = 'http://localhost:8080'
const ACCOUNTS = {
  client: { email: 'client@localhost', password: 'Client@123456' },
  merchant: { email: 'demo@localhost', password: 'Demo@123456' },
  support: { email: 'admin@localhost', password: 'Admin@123456' }
}
const VIEWPORTS: Array<[string, number, number]> = [
  // Tall single-frame captures: full-page stitching duplicates the sticky header.
  ['desktop', 1280, 1700],
  ['phone', 375, 2200]
]
const PAGES: Array<[keyof typeof ACCOUNTS | 'guest', string, string]> = [
  ['guest', 'storefront', '/m/demo'],
  ['guest', 'salon-services', '/m/demo/salon/services'],
  ['guest', 'promotions', '/m/demo/promotions'],
  ['guest', 'contact', '/m/demo/contact'],
  ['guest', 'checkout-empty', '/m/demo/checkout'],
  ['guest', 'signup', '/signup'],
  ['guest', 'client-login', '/account/login'],
  ['client', 'client-home', '/'],
  ['client', 'discover', '/discover'],
  ['client', 'history', '/account/history'],
  ['merchant', 'admin-dashboard', '/m/demo/admin'],
  ['merchant', 'admin-products', '/m/demo/admin/products'],
  ['merchant', 'admin-orders', '/m/demo/admin/orders'],
  ['merchant', 'admin-settings', '/m/demo/admin/settings'],
  ['merchant', 'admin-store', '/m/demo/admin/store'],
  ['merchant', 'admin-plan', '/m/demo/admin/subscription'],
  ['merchant', 'admin-insights', '/m/demo/admin/insights'],
  ['merchant', 'admin-team', '/m/demo/admin/team'],
  ['merchant', 'admin-promotions', '/m/demo/admin/promotions'],
  ['merchant', 'admin-salon', '/m/demo/admin/salon'],
  ['support', 'support-dashboard', '/support'],
  ['support', 'support-merchants', '/support/merchants'],
  ['support', 'support-subscriptions', '/support/subscriptions'],
  ['support', 'support-staff', '/support/staff'],
  ['support', 'support-tickets', '/support/tickets']
]

const tokens: Record<string, string> = {}

describe('layout tour', () => {
  before(() => {
    Object.entries(ACCOUNTS).forEach(([role, creds]) => {
      cy.request('POST', `${API}/api/auth/login`, creds).then((r) => {
        tokens[role] = r.body.token
      })
    })
  })

  VIEWPORTS.forEach(([vp, w, h]) => {
    PAGES.forEach(([role, name, url]) => {
      it(`${vp} ${name}`, () => {
        cy.viewport(w, h)
        cy.visit(url, {
          failOnStatusCode: false,
          onBeforeLoad(win) {
            win.localStorage.clear()
            if (role !== 'guest') win.localStorage.setItem('ps_token', tokens[role])
          }
        })
        cy.wait(2500) // let data load and transitions settle
        cy.screenshot(`${vp}--${name}`, { capture: 'viewport', overwrite: true })
      })
    })
  })
})
