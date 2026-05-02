import Vue from 'vue'
// Use the full build so all components (v-app, v-btn, …) register globally.
// `vuetify/lib` is a-la-carte and needs vuetify-loader in vue.config.js without it you get “Unknown custom element”.
import Vuetify from 'vuetify'

import 'vuetify/dist/vuetify.min.css'
import 'material-design-icons-iconfont/dist/material-design-icons.css'

Vue.use(Vuetify)

export default new Vuetify({
  theme: {
    dark: false,
    options: { customProperties: true },
    themes: {
      light: {
        primary: '#0f172a',
        secondary: '#64748b',
        accent: '#c2410c',
        info: '#0369a1',
        success: '#15803d',
        warning: '#ca8a04',
        error: '#b91c1c',
        anchor: '#ea580c',
        background: '#f8fafc',
        surface: '#ffffff'
      }
    }
  }
})
