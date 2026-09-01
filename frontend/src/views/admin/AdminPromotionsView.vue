<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6 mb-6" elevation="3" rounded="xl">
      <div class="d-flex align-center mb-4">
        <div class="card-label mb-0">Promotions</div>
        <v-spacer />
        <v-btn depressed color="primary" class="text-none" @click="openNew">New promotion</v-btn>
      </div>
      <v-alert v-if="error" type="error" dense outlined class="mb-3">{{ error }}</v-alert>
      <div v-if="!items.length && !loading" class="text-body-2 text--secondary">No promotions yet.</div>
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
            <td>{{ p.startDate }} ? {{ p.endDate }}</td>
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
        <v-text-field v-model="form.title" label="Title" outlined dense />
        <v-textarea v-model="form.description" label="Description" outlined dense rows="2" />
        <v-select v-model="form.discountType" :items="['PERCENTAGE', 'FIXED']" label="Discount type" outlined dense />
        <v-text-field v-model.number="form.discountValue" label="Discount value" type="number" outlined dense />
        <v-text-field v-model.number="form.minimumOrderValue" label="Minimum order (ZAR)" type="number" outlined dense />
        <v-text-field v-model="form.startDate" label="Start date" type="date" outlined dense />
        <v-text-field v-model="form.endDate" label="End date" type="date" outlined dense />
        <v-switch v-model="form.active" label="Active" inset />
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
  created() {
    this.load()
  },
  methods: {
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
    },
    edit(p) {
      this.form = { ...emptyForm(), ...p }
      this.dialog = true
    },
    async save() {
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
