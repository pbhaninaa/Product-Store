/**
 * Browser journey against a running SIT stack (backend `sit` profile + `npm run serve`).
 * Uses the SIT-only demo seed: store slug `demo` with "Demo candle", merchant demo@localhost.
 * PayFast's hosted page is external, so paid settlement is covered by the backend
 * StorePayFastE2ETest; here the customer pays cash and the merchant sees the order.
 */
const MERCHANT = { email: 'demo@localhost', password: 'Demo@123456' }

function field(label: string) {
  return cy.contains('label', label).parents('.v-input').first().find('input')
}

describe('store journey (SIT)', () => {
  let orderId = ''
  const customerEmail = `cy-${Date.now()}@e2e.test`

  it('customer adds a product and places a cash pickup order', () => {
    cy.visit('/m/demo')
    cy.contains('.product-card, [class*=product]', 'Demo candle', { timeout: 20000 })
      .parents()
      .filter(':has(button:contains("Add to cart"))')
      .first()
      .contains('button', 'Add to cart')
      .click()

    // The cart lives in memory, so stay inside the SPA (a full reload would empty it).
    cy.contains('a, button', 'Cart').click()
    cy.location('pathname').should('eq', '/m/demo/checkout')
    field('Full name').clear().type('Cypress Customer')
    field('Email').clear().type(customerEmail)
    field('Phone').clear().type('0820000000')
    cy.contains('button.delivery-option', 'Pickup in store').click()
    cy.contains('button.payment-card', 'Cash').click()
    cy.contains('button', 'Place order').click()

    cy.contains('Your order is in', { timeout: 20000 })
    cy.get('code.success-ref-code')
      .first()
      .invoke('text')
      .then((t) => {
        orderId = t.trim()
        // Customers see the short reference, never the raw id.
        expect(orderId).to.match(/^[0-9A-F]{8}$/)
      })
  })

  it('merchant signs in and sees the new order', () => {
    cy.intercept('POST', '**/api/auth/login').as('login')
    cy.visit('/m/demo/admin')
    field('Email').type(MERCHANT.email)
    field('Password').type(MERCHANT.password, { log: false })
    cy.contains('button', /sign in|log in/i).click()
    cy.wait('@login').its('response.statusCode').should('eq', 200)
    cy.visit('/m/demo/admin/orders')
    cy.contains(customerEmail, { timeout: 20000 })
    cy.contains(`Order ${orderId}`)
  })

  it('an expired session returns the merchant to the sign-in form', () => {
    cy.visit('/m/demo/admin')
    // A structurally valid token whose exp is in the past.
    const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '')
    const expired = `${b64({ alg: 'HS256' })}.${b64({ sub: 'x', roles: ['MERCHANT_OWNER'], tenant: 'demo', exp: 1 })}.sig`
    cy.window().then((w) => w.localStorage.setItem('ps_token', expired))
    cy.visit('/m/demo/admin/orders')
    field('Password').should('be.visible')
  })
})
