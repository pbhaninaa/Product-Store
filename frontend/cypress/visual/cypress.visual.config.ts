import { defineConfig } from 'cypress'

// Layout tour only: a large headless window so tall viewports are captured in one frame.
export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:8085',
    specPattern: 'cypress/visual/**/*.cy.ts',
    supportFile: false,
    video: false,
    setupNodeEvents(on) {
      on('before:browser:launch', (browser, launchOptions) => {
        if (browser.name === 'electron') {
          launchOptions.preferences.width = 1400
          launchOptions.preferences.height = 2400
        }
        return launchOptions
      })
    }
  }
})
