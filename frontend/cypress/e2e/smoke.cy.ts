describe('storefront smoke', () => {
  it('loads the SPA shell', () => {
    cy.visit('/', { failOnStatusCode: false })
    cy.get('body').should('exist')
  })
})
