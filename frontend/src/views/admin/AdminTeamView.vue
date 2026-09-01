<template>
  <div>
    <v-row>
      <v-col cols="12" md="5">
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="card-label mb-4">{{ editId ? 'Edit team member' : 'Add team member' }}</div>
          <v-text-field v-model="form.displayName" outlined hide-details="auto" label="Display name" class="rounded-lg mb-3" />
          <v-text-field
            v-model="form.email"
            outlined
            hide-details="auto"
            label="Login email"
            type="email"
            class="rounded-lg mb-3"
            :disabled="!!editId"
          />
          <v-text-field
            v-if="!editId"
            v-model="form.password"
            outlined
            hide-details="auto"
            label="Password"
            type="password"
            class="rounded-lg mb-3"
          />
          <v-text-field v-model="form.role" outlined hide-details="auto" label="Role label" class="rounded-lg mb-3" />
          <v-select
            v-model="form.payMethod"
            :items="payMethods"
            outlined
            hide-details="auto"
            label="Pay method"
            class="rounded-lg mb-3"
          />
          <v-text-field v-model.number="form.payRate" outlined hide-details="auto" label="Pay rate (ZAR)" type="number" class="rounded-lg mb-3" />
          <v-text-field
            v-model.number="form.bonusPercentage"
            outlined
            hide-details="auto"
            label="Bonus %"
            type="number"
            class="rounded-lg mb-3"
          />
          <v-switch v-if="editId" v-model="form.active" label="Active" inset color="primary" hide-details class="mb-3" />
          <v-alert v-if="error" type="error" dense outlined class="mb-3 rounded-lg">{{ error }}</v-alert>
          <v-btn block x-large depressed class="text-none font-weight-bold btn-admin-primary" :loading="saving" @click="save">
            {{ editId ? 'Save changes' : 'Create staff login' }}
          </v-btn>
          <v-btn v-if="editId" block text class="mt-2 text-none" @click="resetForm">Cancel edit</v-btn>
        </v-card>
      </v-col>
      <v-col cols="12" md="7">
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="card-label mb-4">Login team (payroll)</div>
          <p class="text-caption text--secondary mb-4">
            Separate from extra salon-only names. People you add here also appear under Staff management so you can set
            bookable windows.
          </p>
          <v-progress-linear v-if="loading" indeterminate height="3" class="mb-3" />
          <v-data-table :headers="headers" :items="team" :items-per-page="10" class="elevation-0" no-data-text="No team members yet.">
            <template v-slot:[`item.active`]="{ item }">
              <v-chip small label :color="item.active ? 'success' : 'secondary'" outlined>{{ item.active ? 'Active' : 'Off' }}</v-chip>
            </template>
            <template v-slot:[`item.actions`]="{ item }">
              <v-btn small text color="primary" class="text-none" @click="startEdit(item)">Edit</v-btn>
              <v-btn small text color="error" class="text-none" :disabled="!item.active" @click="deactivate(item)">Deactivate</v-btn>
            </template>
          </v-data-table>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script>
import {
  createTeamMember,
  deactivateTeamMember,
  fetchTeam,
  updateTeamMember
} from '@/services/teamApi'

export default {
  name: 'AdminTeamView',
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    return {
      loading: false,
      saving: false,
      error: '',
      team: [],
      editId: null,
      form: this.emptyForm(),
      payMethods: ['PER_SERVICE', 'PER_HOUR', 'PER_DAY', 'WEEKLY', 'MONTHLY'],
      headers: [
        { text: 'Name', value: 'displayName' },
        { text: 'Email', value: 'email' },
        { text: 'Pay', value: 'payMethod' },
        { text: 'Rate', value: 'payRate' },
        { text: 'Status', value: 'active' },
        { text: '', value: 'actions', sortable: false }
      ]
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user
    }
  },
  watch: {
    user: {
      immediate: true,
      handler(u) {
        if (u) this.load()
      }
    }
  },
  methods: {
    emptyForm() {
      return {
        displayName: '',
        email: '',
        password: '',
        role: 'STAFF',
        payMethod: 'PER_SERVICE',
        payRate: 0,
        bonusPercentage: 0,
        active: true
      }
    },
    resetForm() {
      this.editId = null
      this.form = this.emptyForm()
      this.error = ''
    },
    async load() {
      this.loading = true
      this.error = ''
      try {
        const res = await fetchTeam(this.$route)
        this.team = Array.isArray(res.team) ? res.team : []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load team'
      } finally {
        this.loading = false
      }
    },
    startEdit(item) {
      this.editId = item.id
      this.form = {
        displayName: item.displayName || '',
        email: item.email || '',
        password: '',
        role: item.role || 'STAFF',
        payMethod: item.payMethod || 'PER_SERVICE',
        payRate: item.payRate != null ? item.payRate : 0,
        bonusPercentage: item.bonusPercentage != null ? item.bonusPercentage : 0,
        active: !!item.active
      }
    },
    async save() {
      this.saving = true
      this.error = ''
      try {
        if (this.editId) {
          await updateTeamMember(this.$route, this.editId, {
            displayName: this.form.displayName,
            role: this.form.role,
            payMethod: this.form.payMethod,
            payRate: this.form.payRate,
            bonusPercentage: this.form.bonusPercentage,
            active: this.form.active
          })
        } else {
          await createTeamMember(this.$route, {
            displayName: this.form.displayName,
            email: this.form.email,
            password: this.form.password,
            role: this.form.role,
            payMethod: this.form.payMethod,
            payRate: this.form.payRate,
            bonusPercentage: this.form.bonusPercentage
          })
        }
        this.resetForm()
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Save failed'
      } finally {
        this.saving = false
      }
    },
    async deactivate(item) {
      if (!confirm(`Deactivate ${item.displayName || item.email}?`)) return
      try {
        await deactivateTeamMember(this.$route, item.id)
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Deactivate failed'
      }
    }
  }
}
</script>
