<template>
  <div class="support-page">
    <div class="sc-page-head">
      <div>
        <div class="text-h5 font-weight-bold">Merchants</div>
        <p class="text-body-2 text--secondary mb-0">Search, create, update, or remove merchant workspaces.</p>
      </div>
      <div class="sc-page-head__actions">
        <v-btn class="text-none font-weight-bold" color="primary" outlined @click="loadMerchants" :loading="loadingMerchants">
          <v-icon left small>refresh</v-icon>
          Refresh
        </v-btn>
        <v-btn class="text-none font-weight-bold" color="primary" depressed @click="openCreateDialog">
          <v-icon left small>add</v-icon>
          Add merchant
        </v-btn>
      </div>
    </div>

    <v-alert v-if="error" type="error" outlined dense class="rounded-lg mb-4" dismissible @input="error = ''">{{ error }}</v-alert>

    <v-row dense>
        <v-col cols="12" lg="7">
          <v-card outlined class="rounded-xl pa-4">
            <div class="sc-toolbar">
              <div class="text-h6 font-weight-bold">All merchants</div>
              <v-text-field
                v-model.trim="merchantQuery"
                hide-details
                dense
                outlined
                label="Search by name or slug"
                prepend-inner-icon="search"
                class="support-search rounded-lg ml-sm-auto"
                @keyup.enter="loadMerchants"
              />
            </div>

            <v-data-table
              :headers="merchantHeaders"
              :items="merchants"
              item-key="slug"
              :loading="loadingMerchants"
              loading-text="Loading merchants…"
              class="rounded-lg elevation-0"
              disable-pagination
              hide-default-footer
            >
              <template v-slot:[`item.actions`]="{ item }">
                <div class="d-flex justify-end flex-nowrap">
                  <v-btn icon small title="View" @click.stop="selectMerchant(item)"><v-icon small>visibility</v-icon></v-btn>
                  <v-btn icon small title="Edit" @click.stop="openEditDialog(item)"><v-icon small>edit</v-icon></v-btn>
                  <v-btn v-if="canDelete" icon small title="Delete" @click.stop="openDeleteDialog(item)"><v-icon small color="error">delete</v-icon></v-btn>
                </div>
              </template>
            </v-data-table>
          </v-card>
        </v-col>

        <v-col cols="12" lg="5">
          <v-card outlined class="rounded-xl pa-4 sticky-detail">
            <div class="text-h6 font-weight-bold mb-3">Merchant detail</div>

            <div v-if="!selectedSlug" class="text-body-2 text--secondary">
              Use <strong>View</strong> on a row, or select after search, to load metrics for one tenant.
            </div>

            <template v-else>
              <v-skeleton-loader v-if="loadingDetail && !detail" type="heading, list-item-two-line" />

              <template v-if="detail && detail.merchant">
                <div class="mb-3">
                  <div class="text-h5 font-weight-bold sc-clamp-2">{{ detail.merchant.name }}</div>
                  <div class="text-caption text--secondary mb-3">
                    <code class="sc-code">{{ detail.merchant.slug }}</code>
                    · created {{ formatDate(detail.merchant.createdAt) }}
                  </div>
                  <div class="d-flex flex-wrap sc-gap">
                    <v-btn
                      class="text-none font-weight-bold"
                      color="primary"
                      depressed
                      @click="openPathInNewTab(detail.links && detail.links.storefrontPath)"
                    >
                      Storefront
                    </v-btn>
                    <v-btn
                      v-if="canShadow"
                      class="text-none font-weight-bold"
                      outlined
                      color="primary"
                      :loading="openingAdmin"
                      @click="openAdmin"
                    >
                      Admin
                    </v-btn>
                    <v-btn
                      class="text-none font-weight-bold"
                      outlined
                      color="warning"
                      @click="resetOwnerPassword"
                    >
                      Reset owner password
                    </v-btn>
                  </div>
                  <p v-if="!canShadow" class="text-caption text--secondary mt-2 mb-0">
                    Opening this store's admin needs the <strong>Use shadow help</strong> permission.
                  </p>
                </div>

                <v-row dense>
                  <v-col v-for="t in detailTiles" :key="t.key" cols="12" sm="6" class="d-flex">
                    <v-card outlined class="rounded-xl pa-3 sc-card sc-stat">
                      <div class="text-overline text--secondary sc-stat__label">{{ t.label }}</div>
                      <div class="text-h6 font-weight-bold sc-stat__value">{{ t.value }}</div>
                      <div class="text-caption text--secondary mt-1 sc-stat__caption sc-clamp-2">{{ t.caption }}</div>
                    </v-card>
                  </v-col>
                </v-row>
              </template>
            </template>
          </v-card>
        </v-col>
      </v-row>

    <v-dialog v-model="createDialog" max-width="520" persistent>
      <v-card class="pa-4 rounded-xl">
        <div class="text-h6 font-weight-bold mb-4">Create merchant</div>
        <v-form ref="createForm" @submit.prevent>
          <v-text-field
            v-model="createForm.name"
            outlined
            dense
            label="Store name *"
            class="rounded-lg"
            :rules="[rules.required('Store name'), rules.minLength(LIMITS.NAME_MIN, 'Store name'), rules.maxLength(120, 'Store name')]"
          />
          <v-text-field
            v-model="createForm.slug"
            outlined
            dense
            label="URL slug *"
            hint="Lowercase letters, numbers, hyphens"
            class="rounded-lg"
            :rules="[rules.required('URL slug'), rules.slug()]"
          />
          <v-text-field
            v-model="createForm.ownerEmail"
            outlined
            dense
            label="Owner email *"
            type="email"
            class="rounded-lg"
            :rules="[rules.required('Owner email'), rules.email()]"
          />
          <v-text-field
            v-model="createForm.ownerPassword"
            outlined
            dense
            label="Owner password *"
            type="password"
            class="rounded-lg"
            :rules="[rules.required('Owner password'), rules.password()]"
          />
        </v-form>
        <v-alert v-if="dialogError" type="error" dense outlined class="mt-2 rounded-lg">{{ dialogError }}</v-alert>
        <div class="d-flex justify-end mt-4">
          <v-btn text class="text-none mr-2" @click="createDialog = false">Cancel</v-btn>
          <v-btn color="primary" depressed class="text-none font-weight-bold" :loading="saving" @click="saveCreate">Create</v-btn>
        </div>
      </v-card>
    </v-dialog>

    <v-dialog v-model="editDialog" max-width="520" persistent>
      <v-card class="pa-4 rounded-xl">
        <div class="text-h6 font-weight-bold mb-4">Edit merchant</div>
        <div class="text-caption text--secondary mb-3">Slug in URL: <code class="sc-code">{{ editForm.currentSlug }}</code></div>
        <v-form ref="editForm" @submit.prevent>
          <v-text-field
            v-model="editForm.name"
            outlined
            dense
            label="Store name"
            class="rounded-lg"
            :rules="[rules.minLength(LIMITS.NAME_MIN, 'Store name'), rules.maxLength(120, 'Store name')]"
          />
          <v-text-field
            v-model="editForm.newSlug"
            outlined
            dense
            label="New slug (optional)"
            hint="Leave blank to keep current slug"
            class="rounded-lg"
            :rules="[rules.slug()]"
          />
        </v-form>
        <v-alert v-if="dialogError" type="error" dense outlined class="mt-2 rounded-lg">{{ dialogError }}</v-alert>
        <div class="d-flex justify-end mt-4">
          <v-btn text class="text-none mr-2" @click="editDialog = false">Cancel</v-btn>
          <v-btn color="primary" depressed class="text-none font-weight-bold" :loading="saving" @click="saveEdit">Save</v-btn>
        </div>
      </v-card>
    </v-dialog>

    <v-dialog v-model="deleteDialog" max-width="420" persistent>
      <v-card class="pa-4 rounded-xl">
        <div class="text-h6 font-weight-bold mb-2">Delete merchant?</div>
        <p class="text-body-2">
          This removes tenant <code class="sc-code">{{ deleteSlug }}</code> and related data per platform rules. This cannot be undone.
        </p>
        <v-alert v-if="dialogError" type="error" dense outlined class="mt-2 rounded-lg">{{ dialogError }}</v-alert>
        <div class="d-flex justify-end mt-4">
          <v-btn text class="text-none mr-2" @click="deleteDialog = false">Cancel</v-btn>
          <v-btn color="error" depressed class="text-none font-weight-bold" :loading="saving" @click="confirmDelete">Delete</v-btn>
        </div>
      </v-card>
    </v-dialog>

    <v-dialog v-model="resetPwDialog" max-width="480" persistent>
      <v-card class="pa-4 rounded-xl">
        <div class="text-h6 font-weight-bold mb-2">Reset owner password</div>
        <p class="text-body-2 text--secondary mb-3">
          Set a new password for the merchant owner of <code class="sc-code">{{ selectedSlug }}</code>.
        </p>
        <v-form ref="resetPwForm" @submit.prevent>
          <v-text-field
            v-model="resetPwPassword"
            outlined
            dense
            type="password"
            label="New password *"
            hint="Minimum 8 characters"
            class="rounded-lg"
            hide-details="auto"
            :rules="[rules.required('Password'), rules.password()]"
          />
        </v-form>
        <v-alert v-if="dialogError" type="error" dense outlined class="mt-2 rounded-lg">{{ dialogError }}</v-alert>
        <div class="d-flex justify-end mt-4">
          <v-btn text class="text-none mr-2" @click="resetPwDialog = false">Cancel</v-btn>
          <v-btn
            color="warning"
            depressed
            class="text-none font-weight-bold"
            :loading="saving"
            @click="confirmResetOwnerPassword"
          >
            Reset password
          </v-btn>
        </div>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import { confirmShadowEntry, openMerchantAdminAsShadow, shadowErrorMessage } from './shadowNavigation'
import {
  createSupportMerchant,
  deleteSupportMerchant,
  fetchSupportMerchantDetail,
  fetchSupportMerchants,
  resetMerchantOwnerPassword,
  updateSupportMerchant
} from '@/services/supportApi'
import { formatDate } from '@/utils/displayLabels'
import { LIMITS, rules, validateForm } from '@/utils/validation'

export default {
  name: 'SupportMerchantsView',
  inject: {
    supportDialog: { default: null },
    supportAccess: { default: null }
  },
  data() {
    return {
      merchants: [],
      merchantQuery: '',
      selectedSlug: '',
      detail: null,
      loadingMerchants: false,
      loadingDetail: false,
      openingAdmin: false,
      error: '',
      createDialog: false,
      editDialog: false,
      deleteDialog: false,
      resetPwDialog: false,
      resetPwPassword: '',
      dialogError: '',
      saving: false,
      deleteSlug: '',
      createForm: { name: '', slug: '', ownerEmail: '', ownerPassword: '' },
      editForm: { currentSlug: '', name: '', newSlug: '' },
      rules,
      LIMITS
    }
  },
  computed: {
    canShadow() {
      return Boolean(this.supportAccess && this.supportAccess.can('USE_SHADOW'))
    },
    canDelete() {
      return Boolean(this.supportAccess && this.supportAccess.isPlatformAdmin())
    },
    detailTiles() {
      const d = this.detail || {}
      const orders = d.orders || {}
      const products = d.products || {}
      const salon = d.salon || {}
      const n = (v) => (v === null || v === undefined || v === '' ? 'None' : v)
      return [
        {
          key: 'orders',
          label: 'Orders',
          value: n(orders.total),
          caption: `paid ${n(orders.paid)} · pending ${n(orders.pendingPayment)}`
        },
        {
          key: 'revenue',
          label: 'Paid revenue',
          value: this.formatZar(d.revenue && d.revenue.paidOrdersTotalZar),
          caption: 'Orders marked paid only'
        },
        {
          key: 'products',
          label: 'Products',
          value: `active ${n(products.active)}`,
          caption: `archived included: ${n(products.all)}`
        },
        {
          key: 'salon',
          label: 'Bookings',
          value: `bookings ${n(salon.bookingsTotal)}`,
          caption: `confirmed ${n(salon.bookingsConfirmed)} · services ${n(salon.servicesActive)} · staff ${n(salon.staffActive)}`
        }
      ]
    },
    merchantHeaders() {
      return [
        { text: 'Name', value: 'name', sortable: false },
        { text: 'Slug', value: 'slug', sortable: false },
        { text: 'Orders', value: 'orders', sortable: false, align: 'end' },
        { text: 'Active products', value: 'productsActive', sortable: false, align: 'end' },
        { text: 'Paid revenue', value: 'paidRevenue', sortable: false, align: 'end' },
        { text: '', value: 'actions', sortable: false, align: 'end', width: '120' }
      ]
    }
  },
  created() {
    this.loadMerchants()
  },
  methods: {
    formatDate,
    formatZar(v) {
      if (v === null || v === undefined || v === '') return 'None'
      const n = Number(v)
      if (!Number.isFinite(n)) return String(v)
      try {
        return new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR', maximumFractionDigits: 2 }).format(n)
      } catch {
        return `R ${n.toFixed(2)}`
      }
    },
    async loadMerchants() {
      this.error = ''
      this.loadingMerchants = true
      try {
        const rows = await fetchSupportMerchants(this.merchantQuery)
        this.merchants = (Array.isArray(rows) ? rows : []).map((r) => ({
          name: r && r.name != null ? r.name : '',
          slug: r && r.slug != null ? r.slug : '',
          orders: r && r.totals && r.totals.orders != null ? r.totals.orders : 'None',
          productsActive: r && r.totals && r.totals.productsActive != null ? r.totals.productsActive : 'None',
          paidRevenue: this.formatZar(r && r.revenue ? r.revenue.paidOrdersTotalZar : null)
        }))
      } catch (e) {
        this.error = e && e.message ? e.message : 'Failed to load merchants.'
      } finally {
        this.loadingMerchants = false
      }
    },
    async loadDetail(slug) {
      this.loadingDetail = true
      this.detail = null
      try {
        this.detail = await fetchSupportMerchantDetail(slug)
      } catch (e) {
        this.error = e && e.message ? e.message : 'Failed to load merchant detail.'
      } finally {
        this.loadingDetail = false
      }
    },
    selectMerchant(item) {
      const slug = item && item.slug ? String(item.slug) : ''
      if (!slug) return
      this.selectedSlug = slug
      this.loadDetail(slug)
    },
    async openAdmin() {
      const slug = String(this.selectedSlug || '').trim()
      if (!slug) return
      const label = (this.detail && this.detail.merchant && this.detail.merchant.name) || slug
      if (!(await confirmShadowEntry(this.supportDialog, label))) return
      this.openingAdmin = true
      this.error = ''
      try {
        // Same tab on purpose: the shadow token replaces this browser's session until the agent exits.
        await openMerchantAdminAsShadow(this.$router, slug, 'merchant-admin')
      } catch (e) {
        this.error = shadowErrorMessage(e)
      } finally {
        this.openingAdmin = false
      }
    },
    openPathInNewTab(path) {
      const p = String(path || '').trim()
      if (!p) return
      const url = `${window.location.origin}${p.startsWith('/') ? '' : '/'}${p}`
      window.open(url, '_blank', 'noopener,noreferrer')
    },
    openCreateDialog() {
      this.dialogError = ''
      this.createForm = { name: '', slug: '', ownerEmail: '', ownerPassword: '' }
      this.createDialog = true
      this.$nextTick(() => this.$refs.createForm && this.$refs.createForm.resetValidation())
    },
    async saveCreate() {
      this.dialogError = ''
      if (!validateForm(this.$refs.createForm)) return
      const name = String(this.createForm.name || '').trim()
      const slug = String(this.createForm.slug || '').trim()
      const ownerEmail = String(this.createForm.ownerEmail || '').trim()
      const ownerPassword = String(this.createForm.ownerPassword || '')
      if (!name || !slug || !ownerEmail || !ownerPassword) {
        this.dialogError = 'All fields are required.'
        return
      }
      this.saving = true
      try {
        await createSupportMerchant({ name, slug, ownerEmail, ownerPassword })
        this.createDialog = false
        await this.loadMerchants()
        this.selectedSlug = slug
        await this.loadDetail(slug)
      } catch (e) {
        this.dialogError = e && e.message ? e.message : 'Create failed.'
      } finally {
        this.saving = false
      }
    },
    openEditDialog(item) {
      this.dialogError = ''
      const slug = item && item.slug ? String(item.slug) : ''
      const name = item && item.name ? String(item.name) : ''
      if (!slug) return
      this.editForm = { currentSlug: slug, name, newSlug: '' }
      this.editDialog = true
      this.$nextTick(() => this.$refs.editForm && this.$refs.editForm.resetValidation())
    },
    async saveEdit() {
      this.dialogError = ''
      if (!validateForm(this.$refs.editForm)) return
      const cur = String(this.editForm.currentSlug || '').trim()
      const name = String(this.editForm.name || '').trim()
      const newSlug = String(this.editForm.newSlug || '').trim()
      if (!cur) {
        this.dialogError = 'Missing merchant slug.'
        return
      }
      if (!name && !newSlug) {
        this.dialogError = 'Enter a new name and/or a new slug.'
        return
      }
      const body = {}
      if (name) body.name = name
      if (newSlug) body.slug = newSlug
      this.saving = true
      try {
        await updateSupportMerchant(cur, body)
        this.editDialog = false
        const nextSlug = newSlug || cur
        await this.loadMerchants()
        this.selectedSlug = nextSlug
        await this.loadDetail(nextSlug)
      } catch (e) {
        this.dialogError = e && e.message ? e.message : 'Update failed.'
      } finally {
        this.saving = false
      }
    },
    openDeleteDialog(item) {
      this.dialogError = ''
      const slug = item && item.slug ? String(item.slug) : ''
      if (!slug) return
      this.deleteSlug = slug
      this.deleteDialog = true
    },
    async confirmDelete() {
      this.dialogError = ''
      const slug = String(this.deleteSlug || '').trim()
      if (!slug) return
      this.saving = true
      try {
        await deleteSupportMerchant(slug)
        this.deleteDialog = false
        if (this.selectedSlug === slug) {
          this.selectedSlug = ''
          this.detail = null
        }
        await this.loadMerchants()
      } catch (e) {
        this.dialogError = e && e.message ? e.message : 'Delete failed.'
      } finally {
        this.saving = false
      }
    },
    async resetOwnerPassword() {
      const slug = String(this.selectedSlug || '').trim()
      if (!slug) return
      this.dialogError = ''
      this.resetPwPassword = ''
      this.resetPwDialog = true
      this.$nextTick(() => this.$refs.resetPwForm && this.$refs.resetPwForm.resetValidation())
    },
    async confirmResetOwnerPassword() {
      const slug = String(this.selectedSlug || '').trim()
      if (!validateForm(this.$refs.resetPwForm)) return
      const password = String(this.resetPwPassword || '').trim()
      if (password.length < 8) {
        this.dialogError = 'Password must be at least 8 characters.'
        return
      }
      this.saving = true
      this.error = ''
      this.dialogError = ''
      try {
        const res = await resetMerchantOwnerPassword(slug, password)
        this.resetPwDialog = false
        if (this.supportDialog) {
          await this.supportDialog.info({
            title: 'Password reset',
            message: `New password set for ${res && res.email ? res.email : 'merchant owner'}.`,
            tone: 'success'
          })
        }
      } catch (e) {
        this.dialogError = (e && e.message) || 'Password reset failed (platform admin only).'
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.support-search {
  max-width: 360px;
  width: 100%;
}

@media (min-width: 1280px) {
  .sticky-detail {
    position: sticky;
    top: 64px;
  }
}
</style>
