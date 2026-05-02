<template>
  <div class="support-page">
    <div class="d-flex flex-column flex-md-row align-start align-md-center mb-6">
      <div>
        <div class="text-h5 font-weight-bold">Merchants</div>
        <p class="text-body-2 text--secondary mb-0">Search, create, update, or remove merchant workspaces.</p>
      </div>
      <v-spacer class="d-none d-md-block" />
      <div class="d-flex flex-wrap mt-3 mt-md-0">
        <v-btn class="text-none font-weight-bold" color="primary" outlined @click="loadMerchants" :loading="loadingMerchants">
          <v-icon left small>refresh</v-icon>
          Refresh
        </v-btn>
        <v-btn class="text-none font-weight-bold ml-2" color="primary" depressed @click="openCreateDialog">
          <v-icon left small>add</v-icon>
          Add merchant
        </v-btn>
      </div>
    </div>

    <v-alert v-if="error" type="error" outlined dense class="rounded-lg mb-6">{{ error }}</v-alert>

    <v-row dense>
        <v-col cols="12" lg="7">
          <v-card outlined class="rounded-xl pa-4 mb-6">
            <div class="d-flex flex-column flex-sm-row align-start align-sm-center mb-4">
              <div class="text-h6 font-weight-bold">All merchants</div>
              <v-spacer />
              <v-text-field
                v-model.trim="merchantQuery"
                hide-details
                dense
                outlined
                label="Search by name or slug"
                prepend-inner-icon="search"
                class="support-search rounded-lg mt-3 mt-sm-0"
                @keyup.enter="loadMerchants"
              />
            </div>

            <v-data-table
              :headers="merchantHeaders"
              :items="merchants"
              item-key="slug"
              :loading="loadingMerchants"
              class="rounded-lg elevation-0"
              disable-pagination
              hide-default-footer
            >
              <template v-slot:[`item.actions`]="{ item }">
                <div class="d-flex justify-end flex-nowrap">
                  <v-btn icon small title="View" @click.stop="selectMerchant(item)"><v-icon small>visibility</v-icon></v-btn>
                  <v-btn icon small title="Edit" @click.stop="openEditDialog(item)"><v-icon small>edit</v-icon></v-btn>
                  <v-btn icon small title="Delete" @click.stop="openDeleteDialog(item)"><v-icon small color="error">delete</v-icon></v-btn>
                </div>
              </template>
            </v-data-table>
          </v-card>
        </v-col>

        <v-col cols="12" lg="5">
          <v-card outlined class="rounded-xl pa-4 sticky-detail">
            <div class="text-h6 font-weight-bold mb-4">Merchant detail</div>

            <div v-if="!selectedSlug" class="text-body-2 text--secondary">
              Use <strong>View</strong> on a row, or select after search, to load metrics for one tenant.
            </div>

            <template v-else>
              <v-progress-linear v-if="loadingDetail" indeterminate rounded height="3" class="mb-4" />

              <template v-if="detail && detail.merchant">
                <div class="mb-4">
                  <div class="text-h5 font-weight-bold">{{ detail.merchant.name }}</div>
                  <div class="text-caption text--secondary mb-3">
                    <code class="support-code">{{ detail.merchant.slug }}</code>
                    ù created {{ detail.merchant.createdAt }}
                  </div>
                  <div class="d-flex flex-wrap">
                    <v-btn
                      class="text-none font-weight-bold mr-2 mb-2"
                      color="primary"
                      depressed
                      @click="openPathInNewTab(detail.links && detail.links.storefrontPath)"
                    >
                      Storefront
                    </v-btn>
                    <v-btn
                      class="text-none font-weight-bold mb-2"
                      outlined
                      color="primary"
                      @click="openPathInNewTab(detail.links && detail.links.adminPath)"
                    >
                      Admin
                    </v-btn>
                  </div>
                </div>

                <v-row dense>
                  <v-col cols="6">
                    <v-card outlined class="rounded-xl pa-3">
                      <div class="text-overline text--secondary">Orders</div>
                      <div class="text-h6 font-weight-bold">{{ detail.orders && detail.orders.total }}</div>
                      <div class="text-caption text--secondary mt-1">
                        paid {{ detail.orders && detail.orders.paid }}
                        ù pending {{ detail.orders && detail.orders.pendingPayment }}
                      </div>
                    </v-card>
                  </v-col>
                  <v-col cols="6">
                    <v-card outlined class="rounded-xl pa-3">
                      <div class="text-overline text--secondary">Paid revenue</div>
                      <div class="text-h6 font-weight-bold">{{ formatZar(detail.revenue && detail.revenue.paidOrdersTotalZar) }}</div>
                      <div class="text-caption text--secondary mt-1">Orders marked paid only</div>
                    </v-card>
                  </v-col>
                  <v-col cols="12" sm="6" class="mt-3">
                    <v-card outlined class="rounded-xl pa-3">
                      <div class="text-overline text--secondary">Products</div>
                      <div class="text-h6 font-weight-bold">
                        active {{ detail.products && detail.products.active }}
                      </div>
                      <div class="text-caption text--secondary mt-1">
                        archived included: {{ detail.products && detail.products.all }}
                      </div>
                    </v-card>
                  </v-col>
                  <v-col cols="12" sm="6" class="mt-3">
                    <v-card outlined class="rounded-xl pa-3">
                      <div class="text-overline text--secondary">Salon</div>
                      <div class="text-h6 font-weight-bold">
                        bookings {{ detail.salon && detail.salon.bookingsTotal }}
                      </div>
                      <div class="text-caption text--secondary mt-1">
                        confirmed {{ detail.salon && detail.salon.bookingsConfirmed }}
                        ù services {{ detail.salon && detail.salon.servicesActive }}
                        ù staff {{ detail.salon && detail.salon.staffActive }}
                      </div>
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
        <v-text-field v-model="createForm.name" outlined dense label="Store name" class="rounded-lg" />
        <v-text-field v-model="createForm.slug" outlined dense label="URL slug" hint="Lowercase letters, numbers, hyphens" class="rounded-lg" />
        <v-text-field v-model="createForm.ownerEmail" outlined dense label="Owner email" type="email" class="rounded-lg" />
        <v-text-field v-model="createForm.ownerPassword" outlined dense label="Owner password" type="password" class="rounded-lg" />
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
        <div class="text-caption text--secondary mb-3">Slug in URL: <code class="support-code">{{ editForm.currentSlug }}</code></div>
        <v-text-field v-model="editForm.name" outlined dense label="Store name" class="rounded-lg" />
        <v-text-field
          v-model="editForm.newSlug"
          outlined
          dense
          label="New slug (optional)"
          hint="Leave blank to keep current slug"
          class="rounded-lg"
        />
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
          This removes tenant <code class="support-code">{{ deleteSlug }}</code> and related data per platform rules. This cannot be undone.
        </p>
        <v-alert v-if="dialogError" type="error" dense outlined class="mt-2 rounded-lg">{{ dialogError }}</v-alert>
        <div class="d-flex justify-end mt-4">
          <v-btn text class="text-none mr-2" @click="deleteDialog = false">Cancel</v-btn>
          <v-btn color="error" depressed class="text-none font-weight-bold" :loading="saving" @click="confirmDelete">Delete</v-btn>
        </div>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import {
  createSupportMerchant,
  deleteSupportMerchant,
  fetchSupportMerchantDetail,
  fetchSupportMerchants,
  updateSupportMerchant
} from '@/services/supportApi'

export default {
  name: 'SupportMerchantsView',
  data() {
    return {
      merchants: [],
      merchantQuery: '',
      selectedSlug: '',
      detail: null,
      loadingMerchants: false,
      loadingDetail: false,
      error: '',
      createDialog: false,
      editDialog: false,
      deleteDialog: false,
      dialogError: '',
      saving: false,
      deleteSlug: '',
      createForm: { name: '', slug: '', ownerEmail: '', ownerPassword: '' },
      editForm: { currentSlug: '', name: '', newSlug: '' }
    }
  },
  computed: {
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
    formatZar(v) {
      if (v === null || v === undefined || v === '') return 'ù'
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
          orders: r && r.totals && r.totals.orders != null ? r.totals.orders : 'ù',
          productsActive: r && r.totals && r.totals.productsActive != null ? r.totals.productsActive : 'ù',
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
    },
    async saveCreate() {
      this.dialogError = ''
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
    },
    async saveEdit() {
      this.dialogError = ''
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
    }
  }
}
</script>

<style scoped>
.support-search {
  max-width: 360px;
  width: 100%;
}

.support-code {
  background: rgba(15, 23, 42, 0.06);
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 12px;
}

@media (min-width: 1280px) {
  .sticky-detail {
    position: sticky;
    top: 64px;
  }
}
</style>
