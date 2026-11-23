<template>
  <div class="app-date-field">
    <v-menu
      v-model="menu"
      :close-on-content-click="false"
      :disabled="disabled"
      transition="scale-transition"
      offset-y
      min-width="290px"
    >
      <template #activator="{ on, attrs }">
        <v-text-field
          ref="field"
          :value="displayValue"
          :label="label"
          :rules="boundRules"
          :clearable="clearable"
          :disabled="disabled"
          readonly
          prepend-inner-icon="event"
          v-bind="{ ...$attrs, ...attrs }"
          v-on="on"
          @click:clear="pick('')"
        />
      </template>
      <v-date-picker
        :value="value || undefined"
        :min="min || undefined"
        :max="max || undefined"
        color="primary"
        header-color="primary"
        :first-day-of-week="1"
        no-title
        @input="pick"
      />
    </v-menu>
  </div>
</template>

<script>
import { bindRulesToValue, formatDateLabel } from '@/utils/dateField'

/**
 * Date input that opens the app's Vuetify calendar (same look as the booking page) instead of the
 * browser's native date popup. v-model is an ISO date string (YYYY-MM-DD) or ''. Field props such as
 * outlined, dense and hide-details pass through to the text field. Exposes validate()/resetValidation()
 * like a v-text-field so existing $refs keep working.
 */
export default {
  name: 'AppDateField',
  inheritAttrs: false,
  props: {
    value: { type: String, default: '' },
    label: { type: String, default: '' },
    min: { type: String, default: '' },
    max: { type: String, default: '' },
    rules: { type: Array, default: () => [] },
    clearable: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false }
  },
  data() {
    return { menu: false }
  },
  computed: {
    displayValue() {
      return formatDateLabel(this.value)
    },
    boundRules() {
      return bindRulesToValue(this.rules, () => this.value || '')
    }
  },
  methods: {
    pick(iso) {
      this.menu = false
      const next = iso || ''
      this.$emit('input', next)
      this.$emit('change', next)
      this.$nextTick(() => {
        if (this.$refs.field) this.$refs.field.validate()
      })
    },
    validate(force) {
      return this.$refs.field ? this.$refs.field.validate(force) : true
    },
    resetValidation() {
      if (this.$refs.field) this.$refs.field.resetValidation()
    }
  }
}
</script>
