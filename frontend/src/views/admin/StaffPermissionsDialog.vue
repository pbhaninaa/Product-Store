<template>
  <v-dialog :value="value" max-width="520" scrollable :persistent="saving" @input="$emit('input', $event)">
    <v-card v-if="member" class="rounded-xl">
      <v-card-title class="text-h6 font-weight-bold pb-1">
        What can {{ member.displayName || member.email }} do?
      </v-card-title>
      <v-card-text class="pt-2">
        <p class="text-body-2 text--secondary mb-3">
          Changes apply on their next action — they don’t need to sign in again.
          <template v-if="member.permissionsDefault">They currently have the default staff access.</template>
        </p>

        <div class="card-label mb-1">Areas</div>
        <v-switch
          v-for="p in areas"
          :key="p.key"
          v-model="selected"
          :value="p.key"
          :label="p.label"
          :hint="p.hint"
          persistent-hint
          inset
          dense
          color="primary"
          class="mt-1 mb-2"
          :disabled="saving"
        />

        <div class="card-label mt-4 mb-1">Risky actions</div>
        <v-switch
          v-for="p in risky"
          :key="p.key"
          v-model="selected"
          :value="p.key"
          :label="p.label"
          :hint="p.hint"
          persistent-hint
          inset
          dense
          color="error"
          class="mt-1 mb-2"
          :disabled="saving"
        />

        <v-alert type="info" dense outlined class="mt-4 mb-0 rounded-lg text-body-2">
          Always owner-only: {{ ownerOnly.join(', ') }}. Everyone can see alerts, help, their own income and the plan
          status.
        </v-alert>
        <v-alert v-if="error" type="error" dense outlined class="mt-3 mb-0 rounded-lg">{{ error }}</v-alert>
      </v-card-text>
      <v-card-actions class="flex-wrap px-6 pb-5" style="gap: 10px">
        <v-btn text class="text-none" :disabled="saving" @click="resetToDefault">Use default access</v-btn>
        <v-spacer />
        <v-btn text class="text-none" :disabled="saving" @click="$emit('input', false)">Cancel</v-btn>
        <v-btn depressed color="primary" class="text-none font-weight-bold" :loading="saving" @click="save">
          Save permissions
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
import { updateStaffPermissions } from '@/services/teamApi'
import {
  AREA_PERMISSIONS,
  DEFAULT_STAFF_PERMISSIONS,
  OWNER_ONLY_ACTIONS,
  RISKY_PERMISSIONS
} from '@/utils/merchantPermissions'

/** Owner-only editor for one staff member's permissions (the server rejects anyone else). */
export default {
  name: 'StaffPermissionsDialog',
  props: {
    value: { type: Boolean, default: false },
    /** Team member row from GET /admin/team (has id, displayName, email, permissions, permissionsDefault). */
    member: { type: Object, default: null }
  },
  data() {
    return {
      selected: [],
      saving: false,
      error: '',
      areas: AREA_PERMISSIONS,
      risky: RISKY_PERMISSIONS,
      ownerOnly: OWNER_ONLY_ACTIONS
    }
  },
  watch: {
    value: {
      immediate: true,
      handler(open) {
        if (open) this.loadFromMember()
      }
    }
  },
  methods: {
    loadFromMember() {
      const m = this.member
      this.error = ''
      this.selected = Array.isArray(m && m.permissions) ? [...m.permissions] : [...DEFAULT_STAFF_PERMISSIONS]
    },
    async submit(body) {
      if (!this.member) return
      this.saving = true
      this.error = ''
      try {
        await updateStaffPermissions(this.$route, this.member.id, body)
        this.$emit('saved')
        this.$emit('input', false)
      } catch (e) {
        this.error = (e && e.message) || 'Could not save permissions.'
      } finally {
        this.saving = false
      }
    },
    save() {
      return this.submit({ permissions: [...this.selected] })
    },
    resetToDefault() {
      return this.submit({ resetToDefault: true })
    }
  }
}
</script>
