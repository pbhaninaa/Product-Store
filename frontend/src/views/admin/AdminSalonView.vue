<template>
  <div>
    <v-alert v-if="user && !merchantIsSalon" type="warning" outlined class="rounded-lg mb-6">
      Salon services are available when the store type is <strong>Salon + store</strong> or <strong>Salon only</strong> under
      <strong>Store</strong>, then reload.
    </v-alert>

    <template v-else-if="user">
      <v-card class="admin-card pa-4 pa-md-5 mb-4" elevation="3" rounded="xl">
        <div class="d-flex flex-column flex-md-row align-start">
          <v-avatar color="primary" size="48" class="mb-3 mb-md-0 mr-md-4">
            <v-icon dark>content_cut</v-icon>
          </v-avatar>
          <div>
            <div class="text-h6 font-weight-bold mb-1">Salon services</div>
            <p class="text-body-2 text--secondary mb-0">
              Publish treatments and set price, duration, and images. Use <strong>Staff management</strong> for team and
              weekly hours, and <strong>Payments</strong> for recent checkout activity.
            </p>
          </div>
        </div>
      </v-card>

      <v-row align="stretch" class="admin-products-row">
        <v-col cols="12" md="5" class="d-flex flex-column">
          <v-card class="admin-card pa-4 pa-sm-6 mb-4 mb-md-0" elevation="3" rounded="xl">
            <div class="card-label mb-6">New service</div>
            <v-text-field
              v-model="newService.name"
              outlined
              hide-details="auto"
              label="Service name"
              class="rounded-lg"
              :disabled="newServiceSubmitting"
            />
            <v-textarea
              v-model="newService.description"
              outlined
              hide-details="auto"
              label="Description"
              rows="3"
              hint="Shown to customers on the booking flow."
              persistent-hint
              class="mt-2 rounded-lg"
              :disabled="newServiceSubmitting"
            />
            <v-text-field
              v-model.number="newService.durationMinutes"
              outlined
              hide-details="auto"
              label="Duration (minutes)"
              type="number"
              min="5"
              hint="Minimum 5 minutes. Used to build time slots with staff availability."
              persistent-hint
              class="mt-2 rounded-lg"
              :disabled="newServiceSubmitting"
            />
            <v-text-field
              v-model="newService.priceZar"
              outlined
              hide-details="auto"
              label="Price (ZAR)"
              type="number"
              min="0"
              step="0.01"
              prefix="R"
              class="mt-2 rounded-lg"
              :disabled="newServiceSubmitting"
            />
            <v-file-input
              v-model="newService.file"
              outlined
              hide-details="auto"
              accept="image/*"
              label="Service image"
              prepend-icon="image"
              show-size
              class="mt-2 rounded-lg"
              :disabled="newServiceSubmitting"
              hint="Optional — JPG, PNG, GIF, or WebP."
              persistent-hint
            />
            <v-switch
              v-model="newService.active"
              label="Active"
              inset
              color="primary"
              class="mt-2"
              hide-details
              :disabled="newServiceSubmitting"
            />
            <v-btn
              block
              x-large
              depressed
              class="mt-6 text-none font-weight-bold btn-admin-primary"
              :loading="newServiceSubmitting"
              @click="submitNewService"
            >
              <v-icon left dark>cloud_upload</v-icon>
              Publish service
            </v-btn>
            <v-alert v-if="newServiceError" type="error" dense outlined class="mt-4 rounded-lg">
              {{ newServiceError }}
            </v-alert>
            <v-alert v-if="newServiceSuccess" type="success" dense outlined class="mt-4 rounded-lg">
              Service added — it appears in the catalogue immediately.
            </v-alert>
          </v-card>
        </v-col>

        <v-col cols="12" md="7">
          <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
            <div class="d-flex align-center flex-wrap mb-2">
              <div class="card-label mb-0">Service catalogue</div>
              <v-spacer />
              <v-progress-circular v-if="servicesLoading" indeterminate size="22" width="2" color="accent" />
            </div>
            <p class="text-caption text--secondary mb-4">
              Edit an existing service to change price, duration, text, or image. New services are added using the form on
              the left.
            </p>
            <div v-if="!servicesLoading && !services.length" class="muted-panel rounded-lg pa-6 pa-sm-8 text-center">
              <v-icon size="40" color="secondary" class="mb-3">content_cut</v-icon>
              <div class="text-subtitle-1 font-weight-bold mb-1">No services yet</div>
              <div class="text-body-2 text--secondary">Publish one using the new service form.</div>
            </div>
            <div v-else class="admin-inventory-wrap">
              <div class="admin-inventory-list">
                <div v-for="s in services" :key="s.id" class="admin-inventory-card">
                  <div class="admin-inventory-card__details">
                    <div class="admin-inventory-card__thumb rounded-lg">
                      <v-img
                        v-if="serviceImageSrc(s)"
                        :src="serviceImageSrc(s)"
                        height="72"
                        max-width="72"
                        class="rounded-lg"
                        contain
                      />
                      <div
                        v-else
                        class="d-flex align-center justify-center rounded-lg grey lighten-3"
                        style="width: 72px; height: 72px"
                      >
                        <v-icon color="secondary">image</v-icon>
                      </div>
                    </div>
                    <div class="admin-inventory-card__text">
                      <div class="inventory-product-name">{{ s.name }}</div>
                      <div class="inventory-product-meta d-flex flex-wrap align-center">
                        <span class="list-price inventory-price-pill mb-1">R {{ formatMoney(s.priceZar) }}</span>
                        <span class="inventory-stock-pill text-body-2 mb-1 ml-2">
                          {{ s.durationMinutes }} min
                        </span>
                        <v-chip
                          v-if="!s.active"
                          small
                          label
                          outlined
                          color="secondary"
                          class="text-none ml-2 mb-1"
                        >
                          Inactive
                        </v-chip>
                      </div>
                    </div>
                  </div>
                  <div class="admin-inventory-card__actions">
                    <div class="inventory-actions-bar inventory-actions-bar--edit">
                      <v-btn
                        depressed
                        outlined
                        color="primary"
                        height="44"
                        class="inventory-btn-edit text-none font-weight-bold px-4"
                        :disabled="serviceSaving"
                        @click="openServiceEditDialog(s)"
                      >
                        <v-icon left small color="primary">edit</v-icon>
                        Edit service
                      </v-btn>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </v-card>
        </v-col>
      </v-row>
    </template>

    <v-dialog
      v-model="serviceDialog"
      max-width="560"
      content-class="rounded-xl"
      scrollable
      :persistent="serviceSaving"
      @click:outside="closeServiceDialogIfIdle"
    >
      <v-card v-if="serviceForm.id" class="rounded-xl">
        <v-card-title class="text-h6 font-weight-bold pb-2">Edit service</v-card-title>
        <v-card-text class="pt-2">
          <p class="text-body-2 text--secondary mb-4">
            Update name, price, duration, description, or replace the image. Changes apply to the booking flow immediately.
          </p>
          <v-text-field
            v-model="serviceForm.name"
            outlined
            hide-details="auto"
            label="Service name"
            class="rounded-lg mb-3"
            :disabled="serviceSaving"
          />
          <v-textarea
            v-model="serviceForm.description"
            outlined
            hide-details="auto"
            label="Description"
            rows="3"
            class="rounded-lg mb-3"
            :disabled="serviceSaving"
          />
          <v-text-field
            v-model.number="serviceForm.durationMinutes"
            outlined
            hide-details="auto"
            label="Duration (minutes)"
            type="number"
            min="5"
            class="rounded-lg mb-3"
            :disabled="serviceSaving"
          />
          <v-text-field
            v-model="serviceForm.priceZar"
            outlined
            hide-details="auto"
            label="Price (ZAR)"
            type="number"
            min="0"
            step="0.01"
            prefix="R"
            class="rounded-lg mb-4"
            :disabled="serviceSaving"
          />
          <div class="text-subtitle-2 font-weight-bold mb-2">Image</div>
          <div v-if="serviceEditImageDisplaySrc" class="mb-3">
            <v-img
              :src="serviceEditImageDisplaySrc"
              max-height="160"
              contain
              class="rounded-lg grey lighten-4"
            />
          </div>
          <v-file-input
            :key="'svc-img-' + serviceForm.id"
            v-model="serviceForm.imageFile"
            accept="image/*"
            label="Replace image (optional)"
            prepend-icon="image"
            outlined
            hide-details="auto"
            show-size
            class="rounded-lg"
            :disabled="serviceSaving"
            @change="onServiceEditImageChange"
          />
          <v-switch v-model="serviceForm.active" label="Active" inset color="primary" class="mt-2" hide-details />
          <v-alert v-if="serviceError" type="error" dense outlined class="mt-4 rounded-lg mb-0">
            {{ serviceError }}
          </v-alert>
        </v-card-text>
        <v-card-actions class="px-4 pb-4 pt-0 flex-wrap">
          <v-btn text class="text-none" :disabled="serviceSaving" @click="closeServiceDialogIfIdle">Cancel</v-btn>
          <v-spacer />
          <v-btn
            depressed
            color="primary"
            class="text-none font-weight-bold btn-amber"
            :loading="serviceSaving"
            @click="saveServiceEdit"
          >
            Save service
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import { fetchAdminStoreSettings } from '@/services/adminApi'
import { isSalonShopType } from '@/services/shopType'
import {
  fetchAdminSalonServices,
  upsertAdminSalonService,
  upsertAdminSalonServiceMultipart
} from '@/services/salonAdmin'

export default {
  name: 'AdminSalonView',
  inject: {
    adminSession: { default: null }
  },
  data() {
    return {
      merchantIsSalon: false,
      services: [],
      servicesLoading: false,
      newService: {
        name: '',
        description: '',
        durationMinutes: 60,
        priceZar: '0',
        active: true,
        file: null
      },
      newServiceSubmitting: false,
      newServiceError: '',
      newServiceSuccess: false,
      serviceDialog: false,
      serviceSaving: false,
      serviceError: '',
      serviceForm: {
        id: null,
        name: '',
        description: '',
        durationMinutes: 60,
        priceZar: '0',
        active: true,
        imageFile: null
      },
      serviceEditBaseUrl: '',
      serviceEditObjUrl: ''
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user ? this.adminSession.user : null
    },
    serviceEditImageDisplaySrc() {
      if (this.serviceEditObjUrl) return this.serviceEditObjUrl
      return this.serviceEditBaseUrl || ''
    }
  },
  watch: {
    user(u) {
      if (u) this.bootstrap()
    },
    '$route.params.merchantSlug'() {
      if (this.user) this.bootstrap()
    }
  },
  created() {
    if (this.user) this.bootstrap()
  },
  beforeDestroy() {
    this.clearServiceEditPreviewUrl()
  },
  methods: {
    formatMoney(v) {
      const n = Number(v)
      if (!Number.isFinite(n)) return String(v || '')
      return n.toFixed(2)
    },
    serviceImageSrc(s) {
      const u = s && s.imageUrl != null ? String(s.imageUrl).trim() : ''
      return u || ''
    },
    clearServiceEditPreviewUrl() {
      if (this.serviceEditObjUrl) {
        URL.revokeObjectURL(this.serviceEditObjUrl)
        this.serviceEditObjUrl = ''
      }
    },
    onServiceEditImageChange() {
      this.clearServiceEditPreviewUrl()
      const raw = this.serviceForm.imageFile
      const file = this.pickServiceImageFile(raw)
      if (file) {
        this.serviceEditObjUrl = URL.createObjectURL(file)
      }
    },
    async bootstrap() {
      try {
        const s = await fetchAdminStoreSettings(this.$route)
        this.merchantIsSalon = isSalonShopType(s.shopType)
      } catch {
        this.merchantIsSalon = false
      }
      if (!this.merchantIsSalon) return
      await this.loadServices()
    },
    async loadServices() {
      this.servicesLoading = true
      try {
        this.services = await fetchAdminSalonServices(this.$route, true)
      } catch {
        this.services = []
      } finally {
        this.servicesLoading = false
      }
    },
    resetNewServiceForm() {
      this.newService = {
        name: '',
        description: '',
        durationMinutes: 60,
        priceZar: '0',
        active: true,
        file: null
      }
    },
    pickServiceImageFile(raw) {
      if (!raw) return null
      if (Array.isArray(raw)) return raw[0] instanceof File ? raw[0] : null
      return raw instanceof File ? raw : null
    },
    async submitNewService() {
      this.newServiceError = ''
      this.newServiceSuccess = false
      const name = String(this.newService.name || '').trim()
      if (!name) {
        this.newServiceError = 'Service name is required.'
        return
      }
      const durationMinutes = Math.max(5, Number(this.newService.durationMinutes) || 60)
      const priceZar = Number(this.newService.priceZar) || 0
      const img = this.pickServiceImageFile(this.newService.file)
      this.newServiceSubmitting = true
      try {
        if (img) {
          await upsertAdminSalonServiceMultipart(this.$route, {
            id: null,
            name,
            description: this.newService.description,
            durationMinutes,
            priceZar,
            active: this.newService.active,
            image: img
          })
        } else {
          await upsertAdminSalonService(this.$route, {
            id: null,
            name,
            description: this.newService.description,
            durationMinutes,
            priceZar,
            active: this.newService.active
          })
        }
        this.resetNewServiceForm()
        this.newServiceSuccess = true
        setTimeout(() => {
          this.newServiceSuccess = false
        }, 3500)
        await this.loadServices()
      } catch (e) {
        this.newServiceError = e && e.message ? e.message : 'Could not publish service.'
      } finally {
        this.newServiceSubmitting = false
      }
    },
    openServiceEditDialog(row) {
      if (!row || !row.id) return
      this.serviceError = ''
      this.clearServiceEditPreviewUrl()
      this.serviceEditBaseUrl = this.serviceImageSrc(row)
      this.serviceForm = {
        id: row.id,
        name: row.name || '',
        description: row.description || '',
        durationMinutes: row.durationMinutes || 60,
        priceZar: row.priceZar != null ? String(row.priceZar) : '0',
        active: row.active !== false,
        imageFile: null
      }
      this.serviceDialog = true
    },
    closeServiceDialogIfIdle() {
      if (this.serviceSaving) return
      this.serviceDialog = false
      this.clearServiceEditPreviewUrl()
      this.serviceEditBaseUrl = ''
    },
    async saveServiceEdit() {
      this.serviceError = ''
      this.serviceSaving = true
      try {
        const id = this.serviceForm.id ? String(this.serviceForm.id) : null
        const durationMinutes = Math.max(5, Number(this.serviceForm.durationMinutes) || 60)
        const priceZar = Number(this.serviceForm.priceZar) || 0
        const img = this.pickServiceImageFile(this.serviceForm.imageFile)
        if (img) {
          await upsertAdminSalonServiceMultipart(this.$route, {
            id,
            name: this.serviceForm.name,
            description: this.serviceForm.description,
            durationMinutes,
            priceZar,
            active: this.serviceForm.active,
            image: img
          })
        } else {
          await upsertAdminSalonService(this.$route, {
            id,
            name: this.serviceForm.name,
            description: this.serviceForm.description,
            durationMinutes,
            priceZar,
            active: this.serviceForm.active
          })
        }
        this.serviceDialog = false
        this.clearServiceEditPreviewUrl()
        this.serviceEditBaseUrl = ''
        await this.loadServices()
      } catch (e) {
        this.serviceError = e && e.message ? e.message : 'Could not save service.'
      } finally {
        this.serviceSaving = false
      }
    }
  }
}
</script>

