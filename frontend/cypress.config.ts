import { defineConfig } from 'cypress'

export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:8085',
    specPattern: 'cypress/e2e/**/*.cy.ts',
    supportFile: false,
    video: false
  }
})
