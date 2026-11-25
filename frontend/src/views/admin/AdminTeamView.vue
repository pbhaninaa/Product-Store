<template>
  <div>
    <v-row>
      <v-col cols="12" md="5">
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="card-label mb-4">{{ editId ? 'Edit team member' : 'Add team member' }}</div>
          <v-form ref="teamForm" @submit.prevent>
          <v-text-field
            v-model="form.displayName"
            outlined
            hide-details="auto"
            label="Display name"
            :rules="teamRules.displayName"
            counter="120"
            class="rounded-lg mb-3"
          />
          <v-text-field
            v-model="form.email"
            outlined
            hide-details="auto"
            :label="editId ? 'Login email' : 'Login email *'"
            type="email"
            :rules="editId ? [] : teamRules.email"
            class="rounded-lg mb-3"
            :disabled="!!editId"
          />
          <v-text-field
            v-if="!editId"
            v-model="form.password"
            outlined
            hide-details="auto"
            label="Password *"
            type="password"
            autocomplete="new-password"
            :rules="teamRules.password"
            class="rounded-lg mb-3"
          />
          <v-text-field
            v-model="form.role"
            outlined
            hide-details="auto"
            label="Role label"
            :rules="teamRules.role"
            class="rounded-lg mb-3"
          />
          <div class="text-subtitle-2 font-weight-bold mb-1">Service pay</div>
          <p class="text-caption text--secondary mb-2">For salon bookings this person does.</p>
          <v-select
            v-model="form.payMethod"
            :items="payMethods"
            outlined
            hide-details="auto"
            label="Pay method *"
            :rules="teamRules.payMethod"
            class="rounded-lg mb-3"
          />
          <v-text-field
            v-model.number="form.payRate"
            outlined
            hide-details="auto"
            :label="rateFieldLabel(form.payMethod)"
            type="number"
            min="0"
            :max="isPercentMethod(form.payMethod) ? 100 : undefined"
            step="0.01"
            :rules="payRateRules"
            class="rounded-lg mb-3"
          />
          <div class="text-subtitle-2 font-weight-bold mb-1">Order pay</div>
          <p class="text-caption text--secondary mb-2">
            For completed orders credited to this person. A percentage is of the order total after discounts,
            without the delivery fee.
          </p>
          <v-select
            v-model="form.orderPayMethod"
            :items="orderPayMethods"
            outlined
            hide-details="auto"
            label="Order pay *"
            class="rounded-lg mb-3"
          />
          <v-text-field
            v-if="form.orderPayMethod !== 'NONE'"
            v-model.number="form.orderPayRate"
            outlined
            hide-details="auto"
            :label="rateFieldLabel(form.orderPayMethod, 'Order pay rate')"
            type="number"
            min="0"
            :max="isPercentMethod(form.orderPayMethod) ? 100 : undefined"
            step="0.01"
            :rules="orderPayRateRules"
            class="rounded-lg mb-3"
          />
          <v-text-field
            v-model.number="form.bonusPercentage"
            outlined
            hide-details="auto"
            label="Bonus %"
            type="number"
            min="0"
            max="100"
            :rules="teamRules.bonus"
            class="rounded-lg mb-3"
          />
          <v-switch v-if="editId" v-model="form.active" label="Active" inset color="primary" hide-details class="mb-3" />
          </v-form>
          <v-alert v-if="error" type="error" dense outlined class="mb-3 rounded-lg">{{ error }}</v-alert>
          <v-btn block x-large depressed color="primary" class="text-none font-weight-bold" :loading="saving" @click="save">
            {{ editId ? 'Save changes' : 'Create staff login' }}
          </v-btn>
          <v-btn v-if="editId" block text class="mt-2 text-none" @click="resetForm">Cancel edit</v-btn>
        </v-card>
      </v-col>
      <v-col cols="12" md="7">
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="card-label mb-4">Login team (payroll)</div>
          <p class="text-caption text--secondary mb-4">
            Separate from extra staff names used only for bookings. People you add here also appear under Staff management so you can set
            bookable windows.
          </p>
          <v-progress-linear v-if="loading" indeterminate height="3" class="mb-3" />
          <v-data-table :headers="headers" :items="team" :items-per-page="10" class="elevation-0" no-data-text="No team members yet.">
            <template v-slot:[`item.payMethod`]="{ item }">{{ describeServicePay(item.payMethod, item.payRate) }}</template>
            <template v-slot:[`item.orderPayMethod`]="{ item }">{{ describeOrderPay(item.orderPayMethod, item.orderPayRate) }}</template>
            <template v-slot:[`item.active`]="{ item }">
              <v-chip small label :color="item.active ? 'success' : 'secondary'" outlined>{{ item.active ? 'Active' : 'Off' }}</v-chip>
            </template>
            <template v-slot:[`item.actions`]="{ item }">
              <v-btn small text color="primary" class="text-none" @click="startEdit(item)">Edit</v-btn>
              <v-btn small text color="primary" class="text-none" @click="openPermissions(item)">Permissions</v-btn>
              <v-btn small text color="error" class="text-none" :disabled="!item.active" @click="deactivate(item)">Deactivate</v-btn>
            </template>
          </v-data-table>
          <p class="text-caption text--secondary mt-3 mb-0">
            Use <strong>Permissions</strong> to choose which areas each person can open and whether they may confirm
            payments, delete records, or cancel bookings. New staff start with the default staff access.
          </p>
        </v-card>
      </v-col>
    </v-row>
    <staff-permissions-dialog v-model="permissionsOpen" :member="permissionsMember" @saved="load" />
  </div>
</template>

<script>
import {
  createTeamMember,
  deactivateTeamMember,
  fetchTeam,
  updateTeamMember
} from '@/services/teamApi'
import {
  describeOrderPay,
  describeServicePay,
  isPercentMethod,
  normalizeOrderPayMethod,
  orderPayItems,
  rateFieldLabel,
  rateRules,
  servicePayItems
} from '@/utils/employeePay'
import { rules, validateForm } from '@/utils/validation'
import StaffPermissionsDialog from './StaffPermissionsDialog.vue'

export default {
  name: 'AdminTeamView',
  components: { StaffPermissionsDialog },
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    return {
      permissionsOpen: false,
      permissionsMember: null,
      loading: false,
      saving: false,
      error: '',
      team: [],
      editId: null,
      form: this.emptyForm(),
      payMethods: servicePayItems(),
      orderPayMethods: orderPayItems(),
      teamRules: {
        displayName: [rules.maxLength(120, 'Display name')],
        email: [rules.required('Login email'), rules.email(), rules.maxLength(254, 'Email')],
        password: [rules.required('Password'), rules.password()],
        role: [rules.maxLength(60, 'Role label')],
        payMethod: [rules.required('Pay method')],
        bonus: [rules.percent()]
      },
      headers: [
        { text: 'Name', value: 'displayName' },
        { text: 'Email', value: 'email' },
        { text: 'Service pay', value: 'payMethod' },
        { text: 'Order pay', value: 'orderPayMethod' },
        { text: 'Status', value: 'active' },
        { text: '', value: 'actions', sortable: false }
      ]
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user
    },
    payRateRules() {
      return rateRules(this.form.payMethod, 'Pay rate')
    },
    orderPayRateRules() {
      return rateRules(this.form.orderPayMethod, 'Order pay rate')
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
    describeOrderPay,
    describeServicePay,
    isPercentMethod,
    rateFieldLabel,
    emptyForm() {
      return {
        displayName: '',
        email: '',
        password: '',
        role: 'STAFF',
        payMethod: 'PER_SERVICE',
        payRate: 0,
        bonusPercentage: 0,
        orderPayMethod: 'NONE',
        orderPayRate: 0,
        active: true
      }
    },
    /** Pay fields sent on create and update; order pay rate is left out while order pay is off. */
    payBody() {
      const orderOn = this.form.orderPayMethod !== 'NONE'
      return {
        payMethod: this.form.payMethod,
        payRate: this.form.payRate,
        bonusPercentage: this.form.bonusPercentage,
        orderPayMethod: this.form.orderPayMethod,
        ...(orderOn ? { orderPayRate: this.form.orderPayRate } : {})
      }
    },
    resetForm() {
      this.editId = null
      this.form = this.emptyForm()
      this.error = ''
      this.resetTeamValidation()
    },
    resetTeamValidation() {
      this.$nextTick(() => {
        if (this.$refs.teamForm) this.$refs.teamForm.resetValidation()
      })
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
        orderPayMethod: normalizeOrderPayMethod(item.orderPayMethod),
        orderPayRate: item.orderPayRate != null ? item.orderPayRate : 0,
        active: !!item.active
      }
      this.resetTeamValidation()
    },
    async save() {
      if (!validateForm(this.$refs.teamForm)) return
      this.saving = true
      this.error = ''
      try {
        if (this.editId) {
          await updateTeamMember(this.$route, this.editId, {
            displayName: this.form.displayName,
            role: this.form.role,
            ...this.payBody(),
            active: this.form.active
          })
        } else {
          await createTeamMember(this.$route, {
            displayName: this.form.displayName,
            email: this.form.email,
            password: this.form.password,
            role: this.form.role,
            ...this.payBody()
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
    openPermissions(item) {
      this.permissionsMember = item
      this.permissionsOpen = true
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
