const { defineConfig } = require('vitest/config')
const path = require('path')

module.exports = defineConfig({
  test: {
    environment: 'node',
    include: ['tests/**/*.test.{js,mjs,ts}']
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  }
})
