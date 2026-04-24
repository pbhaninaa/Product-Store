import Vue from 'vue'
// Use the full build so all components (v-app, v-btn, …) register globally.
// `vuetify/lib` is a-la-carte and needs vuetify-loader in vue.config.js without it you get “Unknown custom element”.
import Vuetify from 'vuetify'
/* eslint-disable no-unused-vars */
import { getThemeByStoreType } from '@/themes'
/* eslint-enable no-unused-vars */

import 'vuetify/dist/vuetify.min.css'
import 'material-design-icons-iconfont/dist/material-design-icons.css'

Vue.use(Vuetify)

// Initialize Vuetify with default colognes theme
const vuetifyInstance = new Vuetify({
  theme: {
    dark: false,
    options: { customProperties: true },
    themes: {
      light: getThemeByStoreType('colognes') // Default theme
    }
  }
})

/**
 * Update the active theme based on store type
 * @param {string} storeType - Store type to apply theme for
 */
export function setThemeByStoreType(storeType = 'colognes') {
  const theme = getThemeByStoreType(storeType)
  vuetifyInstance.framework.theme.themes.light = theme
}

export default vuetifyInstance
