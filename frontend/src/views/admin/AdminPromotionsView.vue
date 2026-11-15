<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6 mb-6" elevation="3" rounded="xl">
      <div class="d-flex align-center mb-4">
        <div class="card-label mb-0">Promotions</div>
        <v-spacer />
        <v-btn depressed color="primary" class="text-none" @click="openNew">New promotion</v-btn>
      </div>
      <v-alert v-if="error" type="error" dense outlined class="mb-3">{{ error }}</v-alert>
      <v-skeleton-loader v-if="loading && !items.length" type="table-heading, table-row-divider@3" />
      <div v-else-if="!items.length" class="text-body-2 text--secondary">No promotions yet.</div>
      <v-simple-table v-else>
        <thead>
          <tr>
            <th>Title</th>
            <th>Type</th>
            <th>Value</th>
            <th>Dates</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in items" :key="p.id">
            <td>{{ p.title }}</td>
            <td>{{ p.discountType }}</td>
            <td>{{ p.discountValue }}</td>
            <td class="text-no-wrap">{{ promoDateRange(p) }}</td>
            <td class="text-right">
              <v-btn x-small text @click="edit(p)">Edit</v-btn>
              <v-btn x-small text color="error" @click="remove(p)">Delete</v-btn>
            </td>
          </tr>
        </tbody>
      </v-simple-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="520">
      <v-card class="pa-4">
        <div class="font-weight-bold mb-4">{{ form.id ? 'Edit promotion' : 'New promotion' }}</div>
        <v-form ref="promoForm" @submit.prevent>
          <v-text-field
            v-model="form.title"
            label="Title *"
            outlined
            dense
            counter="200"
            :rules="promoRules.title"
          />
          <v-textarea
            v-model="form.description"
            label="Description"
            outlined
            dense
            rows="2"
            counter="2000"
            :rules="promoRules.description"
          />
          <v-select
            v-model="form.discountType"
            :items="['PERCENTAGE', 'FIXED']"
            label="Discount type *"
            outlined
            dense
            :rules="promoRules.discountType"
          />
          <v-text-field
            v-model.number="form.discountValue"
            :label="form.discountType === 'FIXED' ? 'Discount value (ZAR) *' : 'Discount value (%) *'"
            type="number"
            min="0"
            outlined
            dense
            :rules="promoRules.discountValue"
          />
          <v-text-field
            v-model.number="form.minimumOrderValue"
            label="Minimum order (ZAR)"
            type="number"
            min="0"
            step="0.01"
            outlined
            dense
            :rules="promoRules.minimumOrderValue"
          />
          <v-text-field
            v-model="form.startDate"
            label="Start date *"
            type="date"
            outlined
            dense
            :rules="promoRules.startDate"
          />
          <v-text-field
            v-model="form.endDate"
            label="End date *"
            type="date"
            :min="form.startDate || undefined"
            outlined
            dense
            :rules="promoRules.endDate"
          />
          <v-switch v-model="form.active" label="Active" inset />
        </v-form>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="dialog = false">Cancel</v-btn>
          <v-btn depressed color="primary" :loading="saving" @click="save">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import { apiFetch } from '@/services/api'
import { requireMerchantSlugForApi } from '@/services/auth'
import { isBlank, LIMITS, rules, validateForm } from '@/utils/validation'

function emptyForm() {
  return {
    id: '',
    title: '',
    description: '',
    discountType: 'PERCENTAGE',
    discountValue: 10,
    minimumOrderValue: 0,
    startDate: '',
    endDate: '',
    active: true
  }
}

export default {
  name: 'AdminPromotionsView',
  data() {
    return { items: [], loading: false, error: '', dialog: false, saving: false, form: emptyForm() }
  },
  computed: {
    promoRules() {
      const fixed = this.form.discountType === 'FIXED'
      return {
        title: [rules.required('Title'), rules.maxLength(LIMITS.TITLE_MAX, 'Title')],
        description: [rules.maxLength(2000, 'Description')],
        discountType: [rules.required('Discount type')],
        discountValue: [
          rules.required('Discount value'),
          fixed ? rules.money({ allowZero: false }) : rules.percent(),
          (v) => isBlank(v) || Number(v) > 0 || 'Discount must be more than 0'
        ],
        minimumOrderValue: [rules.money({ min: 0 })],
        startDate: [rules.required('Start date')],
        endDate: [rules.required('End date'), rules.notBefore(() => this.form.startDate)]
      }
    }
  },
  created() {
    this.load()
  },
  methods: {
    promoDateRange(p) {
      const start = p && p.startDate ? String(p.startDate) : ''
      const end = p && p.endDate ? String(p.endDate) : ''
      if (start && end) return `${start} – ${end}`
      if (start) return `From ${start}`
      if (end) return `Until ${end}`
      return 'No end date'
    },
    slug() {
      return requireMerchantSlugForApi(this.$route)
    },
    async load() {
      this.loading = true
      this.error = ''
      try {
        const res = await apiFetch(`/api/m/${encodeURIComponent(this.slug())}/admin/promotions`, { auth: true })
        this.items = Array.isArray(res) ? res : []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load'
      } finally {
        this.loading = false
      }
    },
    openNew() {
      this.form = emptyForm()
      this.dialog = true
      this.resetPromoValidation()
    },
    edit(p) {
      this.form = { ...emptyForm(), ...p }
      this.dialog = true
      this.resetPromoValidation()
    },
    resetPromoValidation() {
      this.$nextTick(() => {
        if (this.$refs.promoForm) this.$refs.promoForm.resetValidation()
      })
    },
    async save() {
      if (!this.dialog || !validateForm(this.$refs.promoForm)) return
      this.saving = true
      try {
        const body = { ...this.form }
        if (this.form.id) {
          await apiFetch(`/api/m/${encodeURIComponent(this.slug())}/admin/promotions/${this.form.id}`, {
            method: 'PUT',
            json: body,
            auth: true
          })
        } else {
          await apiFetch(`/api/m/${encodeURIComponent(this.slug())}/admin/promotions`, {
            method: 'POST',
            json: body,
            auth: true
          })
        }
        this.dialog = false
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Could not save'
      } finally {
        this.saving = false
      }
    },
    async remove(p) {
      try {
        await apiFetch(`/api/m/${encodeURIComponent(this.slug())}/admin/promotions/${p.id}`, {
          method: 'DELETE',
          auth: true
        })
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Could not delete'
      }
    }
  }
}
</script>
