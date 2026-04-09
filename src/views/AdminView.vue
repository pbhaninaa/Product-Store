<template>
  <div>
    <section class="admin-hero">
      <v-container class="py-10 py-md-12">
        <div class="admin-hero__inner d-flex flex-column flex-md-row align-start align-md-center">
          <div>
            <div class="admin-kicker mb-2">
              <v-icon small color="white" class="mr-1">lock</v-icon>
              Protected area
            </div>
            <h1 class="admin-title">Admin</h1>
            <p class="admin-lead mb-0">
              Sign in, upload images to Supabase Storage, and sync products to Postgres — all from here.
            </p>
          </div>
          <v-chip
            class="mt-6 mt-md-0 ml-md-auto text-none font-weight-bold px-4"
            color="white"
            outlined
            label
          >
            <v-icon left small color="white">cloud_done</v-icon>
            Live sync
          </v-chip>
        </div>
      </v-container>
    </section>

    <v-container class="pb-12 pb-md-16 admin-body">
      <v-alert
        v-if="supabaseConfigHint"
        type="warning"
        prominent
        border="left"
        colored-border
        class="mb-8 rounded-lg"
      >
        {{ supabaseConfigHint }}
      </v-alert>

      <v-row>
        <v-col cols="12" md="5">
          <v-card class="admin-card pa-6 mb-6" elevation="3" rounded="xl">
            <div class="card-label mb-6">Authentication</div>

            <div v-if="user" class="d-flex flex-column flex-sm-row align-start align-sm-center">
              <v-avatar color="success" size="44" class="mr-sm-4 mb-4 mb-sm-0">
                <v-icon dark>person</v-icon>
              </v-avatar>
              <div class="flex-grow-1">
                <div class="text-subtitle-1 font-weight-bold">Signed in</div>
                <div class="text-body-2 text--secondary text-truncate">{{ user.email }}</div>
              </div>
              <v-btn text color="error" class="mt-4 mt-sm-0 text-none font-weight-bold" @click="doLogout">
                Sign out
              </v-btn>
            </div>

            <div v-else>
              <v-text-field
                v-model="email"
                outlined
                hide-details="auto"
                label="Email"
                type="email"
                autocomplete="email"
                class="rounded-lg"
                :disabled="authLoading"
              />
              <v-text-field
                v-model="password"
                outlined
                hide-details="auto"
                label="Password"
                type="password"
                autocomplete="current-password"
                class="mt-4 rounded-lg"
                :disabled="authLoading"
              />

              <v-btn
                block
                x-large
                depressed
                class="mt-6 text-none font-weight-bold btn-admin-primary"
                :loading="authLoading"
                @click="doLogin"
              >
                Sign in
              </v-btn>

              <v-alert v-if="authError" type="error" dense outlined class="mt-4 rounded-lg">
                {{ authError }}
              </v-alert>

              <p class="text-caption text--secondary mt-4 mb-0">
                Enable <strong>Email</strong> in Supabase → Authentication → Providers.
              </p>
            </div>
          </v-card>

          <v-card class="admin-card pa-6" elevation="3" rounded="xl">
            <div class="card-label mb-6">New product</div>

            <div v-if="!user" class="muted-panel rounded-lg pa-6 text-center">
              <v-icon color="secondary" class="mb-2">upload_file</v-icon>
              <div class="text-body-2 text--secondary">Sign in to upload products.</div>
            </div>

            <div v-else>
              <v-text-field
                v-model="name"
                outlined
                hide-details="auto"
                label="Product name"
                class="rounded-lg"
                :disabled="submitting"
              />

              <v-text-field
                v-model="price"
                outlined
                hide-details="auto"
                label="Price (e.g. 100)"
                type="number"
                class="mt-2 rounded-lg"
                :disabled="submitting"
              />

              <v-file-input
                v-model="file"
                outlined
                hide-details="auto"
                accept="image/*"
                label="Product image"
                class="mt-2 rounded-lg"
                :disabled="submitting"
                prepend-icon="image"
                show-size
              />

              <v-btn
                block
                x-large
                depressed
                class="mt-6 text-none font-weight-bold btn-admin-primary"
                :loading="submitting"
                @click="submit"
              >
                <v-icon left dark>cloud_upload</v-icon>
                Publish product
              </v-btn>

              <v-alert v-if="submitError" type="error" dense outlined class="mt-4 rounded-lg">
                {{ submitError }}
              </v-alert>
            <v-alert v-if="submitSuccess" type="success" dense outlined class="mt-4 rounded-lg">
              Product uploaded — it should appear on the shop immediately.
            </v-alert>
            </div>
          </v-card>
        </v-col>

        <v-col cols="12" md="7">
          <v-card class="admin-card pa-6" elevation="3" rounded="xl">
            <div class="d-flex align-center flex-wrap mb-2">
              <div class="card-label mb-0">Inventory</div>
              <v-spacer />
              <v-progress-circular v-if="loading" indeterminate size="22" width="2" color="accent" />
            </div>
            <p class="text-caption text--secondary mb-4">
              While signed in, use <strong>Delete</strong> to remove a product from the shop and its image from storage.
            </p>

            <v-alert v-if="deleteError" type="error" dense outlined class="mb-4 rounded-lg">
              {{ deleteError }}
            </v-alert>

            <div v-if="!loading && products.length === 0" class="muted-panel rounded-lg pa-8 text-center">
              <v-icon size="40" color="secondary" class="mb-3">inventory</v-icon>
              <div class="text-subtitle-1 font-weight-bold mb-1">No products yet</div>
              <div class="text-body-2 text--secondary">Add one using the form on the left.</div>
            </div>

            <v-list v-else three-line class="admin-list py-0">
              <v-list-item
                v-for="p in products"
                :key="p.id"
                class="admin-list-item px-4 py-3 mb-3"
              >
                <v-list-item-avatar tile size="64" class="rounded-lg mr-4 my-0">
                  <v-img :src="p.imageUrl">
                    <template #placeholder>
                      <v-row class="fill-height ma-0" align="center" justify="center">
                        <v-progress-circular indeterminate size="20" width="2" />
                      </v-row>
                    </template>
                  </v-img>
                </v-list-item-avatar>

                <v-list-item-content>
                  <v-list-item-title class="list-title font-weight-bold">
                    {{ p.name }}
                  </v-list-item-title>
                  <v-list-item-subtitle class="list-price">
                    {{ formatZar(p.price) }}
                  </v-list-item-subtitle>
                </v-list-item-content>

                <v-list-item-action class="ma-0 ml-2 align-self-center flex-column flex-sm-row">
                  <v-btn
                    small
                    depressed
                    color="error"
                    class="text-none white--text"
                    :disabled="!user || deletingId === p.id"
                    :loading="deletingId === p.id"
                    @click="remove(p)"
                  >
                    <v-icon left small color="white">delete</v-icon>
                    Delete
                  </v-btn>
                </v-list-item-action>
              </v-list-item>
            </v-list>
          </v-card>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script>
import { createProduct, deleteProduct, subscribeToProducts } from '@/services/products'
import { loginWithEmailPassword, logout, subscribeToAuth } from '@/services/auth'
import { supabaseSetupMessage } from '@/supabase'
import { formatZar } from '@/utils/price'

export default {
  name: 'AdminView',
  data() {
    return {
      user: null,
      email: '',
      password: '',
      authLoading: false,
      authError: '',
      name: '',
      price: '',
      file: null,
      submitting: false,
      submitError: '',
      submitSuccess: false,
      loading: true,
      products: [],
      deletingId: null,
      deleteError: '',
      supabaseConfigHint: supabaseSetupMessage
    }
  },
  created() {
    this.unsubAuth = subscribeToAuth((user) => {
      this.user = user
    })
    this.unsub = subscribeToProducts((products) => {
      this.products = products
      this.loading = false
    })
  },
  beforeDestroy() {
    if (this.unsubAuth) this.unsubAuth()
    if (this.unsub) this.unsub()
  },
  methods: {
    formatZar,
    async doLogin() {
      this.authError = ''
      this.authLoading = true
      try {
        await loginWithEmailPassword(this.email, this.password)
        this.email = ''
        this.password = ''
      } catch (e) {
        this.authError = e && e.message ? e.message : 'Sign in failed.'
      } finally {
        this.authLoading = false
      }
    },
    async doLogout() {
      await logout()
    },
    async submit() {
      this.submitError = ''
      this.submitSuccess = false
      this.submitting = true

      try {
        await createProduct({
          name: this.name,
          price: this.price,
          file: this.file
        })
        this.name = ''
        this.price = ''
        this.file = null
        this.submitSuccess = true
        setTimeout(() => (this.submitSuccess = false), 2500)
      } catch (e) {
        this.submitError = e && e.message ? e.message : 'Upload failed.'
      } finally {
        this.submitting = false
      }
    },
    async remove(p) {
      if (!this.user) return
      this.deleteError = ''
      const ok = window.confirm(`Delete "${p.name}"? This removes it from the shop and deletes the image file.`)
      if (!ok) return

      const imagePath = p.imagePath || p.image_path
      this.deletingId = p.id
      try {
        await deleteProduct({ id: p.id, imagePath })
        this.deleteError = ''
      } catch (e) {
        this.deleteError = e && e.message ? e.message : 'Delete failed.'
      } finally {
        this.deletingId = null
      }
    }
  }
}
</script>

<style scoped>
.admin-hero {
  background: linear-gradient(135deg, #0f172a 0%, #1e3a5f 52%, #7c2d12 100%);
  color: #f8fafc;
  position: relative;
  overflow: hidden;
}

.admin-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(500px 280px at 15% 20%, rgba(234, 88, 12, 0.25), transparent 70%),
    radial-gradient(400px 220px at 90% 0%, rgba(148, 163, 184, 0.2), transparent 65%);
  pointer-events: none;
}

.admin-hero__inner {
  position: relative;
  z-index: 1;
}

.admin-kicker {
  display: inline-flex;
  align-items: center;
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(248, 250, 252, 0.65);
}

.admin-title {
  font-size: clamp(1.75rem, 3vw, 2.25rem);
  font-weight: 700;
  letter-spacing: -0.035em;
  line-height: 1.15;
}

.admin-lead {
  margin-top: 0.75rem;
  max-width: 48ch;
  color: rgba(248, 250, 252, 0.78);
  line-height: 1.6;
  font-size: 1rem;
}

.admin-body {
  margin-top: -32px;
}

.admin-card {
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.card-label {
  font-size: 0.6875rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
}

.muted-panel {
  border: 1px dashed rgba(15, 23, 42, 0.12);
  background: rgba(248, 250, 252, 0.85);
}

.btn-admin-primary {
  border-radius: 14px !important;
  background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%) !important;
  color: #fff !important;
  box-shadow: 0 12px 32px -12px rgba(194, 65, 12, 0.65) !important;
}

.admin-list {
  background: transparent !important;
}

.admin-list-item {
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 16px !important;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 24px -18px rgba(15, 23, 42, 0.35);
}

.list-title {
  font-size: 1rem !important;
  letter-spacing: -0.02em;
  line-height: 1.35 !important;
}

.list-price {
  margin-top: 6px !important;
  font-size: 0.9375rem !important;
  font-weight: 700 !important;
  background: linear-gradient(120deg, #c2410c, #ea580c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent !important;
}
</style>
