<template>
  <v-dialog v-model="open" :max-width="maxWidth" persistent content-class="support-action-dialog">
    <v-card class="admin-card pa-4 pa-sm-6 rounded-xl" elevation="8">
      <div class="d-flex align-start mb-4">
        <v-avatar v-if="tone === 'danger'" color="red lighten-5" size="44" class="mr-3 flex-shrink-0">
          <v-icon color="error">warning</v-icon>
        </v-avatar>
        <v-avatar v-else-if="tone === 'success'" color="green lighten-5" size="44" class="mr-3 flex-shrink-0">
          <v-icon color="success">check_circle</v-icon>
        </v-avatar>
        <v-avatar v-else color="blue-grey lighten-5" size="44" class="mr-3 flex-shrink-0">
          <v-icon color="primary">info</v-icon>
        </v-avatar>
        <div class="flex-grow-1">
          <div class="text-h6 font-weight-bold">{{ title }}</div>
          <p v-if="message" class="text-body-2 text--secondary mb-0 mt-2">{{ message }}</p>
          <p v-if="hint" class="text-caption text--secondary mb-0 mt-2">{{ hint }}</p>
        </div>
      </div>

      <v-text-field
        v-if="mode === 'prompt'"
        v-model="inputValue"
        outlined
        dense
        hide-details="auto"
        class="rounded-lg mb-3"
        :label="inputLabel"
        :type="inputType"
        :placeholder="inputPlaceholder"
        :autofocus="open"
        @keyup.enter="onConfirm"
      />

      <v-select
        v-if="mode === 'select'"
        v-model="inputValue"
        :items="selectItems"
        outlined
        dense
        hide-details="auto"
        class="rounded-lg mb-3"
        :label="inputLabel"
      />

      <v-alert v-if="localError" type="error" dense outlined class="rounded-lg mb-3">{{ localError }}</v-alert>

      <div class="d-flex justify-end flex-wrap">
        <v-btn
          v-if="showCancel"
          text
          class="text-none mr-2 mb-1"
          :disabled="loading"
          @click="onCancel"
        >
          {{ cancelLabel }}
        </v-btn>
        <v-btn
          depressed
          class="text-none font-weight-bold mb-1"
          :color="confirmColor"
          :loading="loading"
          @click="onConfirm"
        >
          {{ confirmLabel }}
        </v-btn>
      </div>
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  name: 'SupportActionDialog',
  data() {
    return {
      open: false,
      mode: 'confirm',
      title: '',
      message: '',
      hint: '',
      inputLabel: '',
      inputPlaceholder: '',
      inputType: 'text',
      inputValue: '',
      selectItems: [],
      confirmLabel: 'Confirm',
      cancelLabel: 'Cancel',
      confirmColor: 'primary',
      tone: 'default',
      maxWidth: 480,
      showCancel: true,
      loading: false,
      localError: '',
      requiredPhrase: '',
      validator: null,
      resolver: null
    }
  },
  methods: {
    _show(opts) {
      return new Promise((resolve, reject) => {
        this.resolver = { resolve, reject }
        this.mode = opts.mode || 'confirm'
        this.title = opts.title || 'Confirm'
        this.message = opts.message || ''
        this.hint = opts.hint || ''
        this.inputLabel = opts.inputLabel || ''
        this.inputPlaceholder = opts.inputPlaceholder || ''
        this.inputType = opts.inputType || 'text'
        this.inputValue = opts.value != null ? String(opts.value) : ''
        this.selectItems = opts.items || []
        this.confirmLabel = opts.confirmLabel || 'Confirm'
        this.cancelLabel = opts.cancelLabel || 'Cancel'
        this.confirmColor = opts.confirmColor || (opts.tone === 'danger' ? 'error' : 'primary')
        this.tone = opts.tone || 'default'
        this.maxWidth = opts.maxWidth || 480
        this.showCancel = opts.showCancel !== false
        this.loading = false
        this.localError = ''
        this.requiredPhrase = opts.requiredPhrase || ''
        this.validator = typeof opts.validate === 'function' ? opts.validate : null
        this.open = true
      })
    },
    confirm(opts = {}) {
      return this._show({ ...opts, mode: 'confirm' })
    },
    prompt(opts = {}) {
      return this._show({ ...opts, mode: 'prompt' })
    },
    select(opts = {}) {
      return this._show({ ...opts, mode: 'select' })
    },
    info(opts = {}) {
      return this._show({
        ...opts,
        mode: 'confirm',
        tone: opts.tone || 'success',
        confirmLabel: opts.confirmLabel || 'OK',
        showCancel: false,
        confirmColor: opts.confirmColor || 'primary'
      })
    },
    onCancel() {
      this.open = false
      if (this.resolver) this.resolver.reject(new Error('cancelled'))
      this.resolver = null
    },
    onConfirm() {
      this.localError = ''
      const val = this.mode === 'confirm' ? true : String(this.inputValue || '').trim()

      if (this.mode === 'prompt' || this.mode === 'select') {
        if (this.requiredPhrase && val !== this.requiredPhrase) {
          this.localError = `Type ${this.requiredPhrase} exactly to confirm.`
          return
        }
        if (this.validator) {
          const err = this.validator(val)
          if (err) {
            this.localError = err
            return
          }
        }
      }

      this.open = false
      if (this.resolver) this.resolver.resolve(val)
      this.resolver = null
    }
  }
}
</script>
