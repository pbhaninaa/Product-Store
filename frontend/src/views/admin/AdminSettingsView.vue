<template>
  <div v-if="user">
    <v-card class="admin-card pa-4 pa-sm-6 mb-6" elevation="3" rounded="xl">
      <div class="card-label mb-2">Settings</div>
      <p class="text-body-2 text--secondary mb-0">
        Set up your catalogue, team, store details, and billing. Day-to-day work (orders, bookings, alerts) stays in the
        tabs above.
      </p>
    </v-card>

    <section v-for="group in settingsGroups" :key="group.key" class="mb-8">
      <div class="card-label mb-3">{{ group.title }}</div>
      <v-row dense>
        <v-col v-for="item in group.items" :key="item.name" cols="12" sm="6" lg="4">
          <v-card
            :to="item.to"
            class="admin-settings-card pa-4 rounded-xl h-100"
            elevation="2"
            hover
            ripple
          >
            <div class="d-flex align-start">
              <v-badge
                :value="item.badgeCount > 0"
                :content="item.badgeCount > 99 ? '99+' : String(item.badgeCount)"
                color="deep-orange darken-2"
                overlap
                offset-x="6"
                offset-y="6"
              >
                <v-avatar :color="item.color" size="44" class="mr-3 flex-shrink-0">
                  <v-icon dark size="22">{{ item.icon }}</v-icon>
                </v-avatar>
              </v-badge>
              <div class="flex-grow-1 min-width-0">
                <div class="text-subtitle-1 font-weight-bold mb-1">{{ item.title }}</div>
                <p class="text-body-2 text--secondary mb-2">{{ item.blurb }}</p>
                <span class="text-caption font-weight-bold primary--text text-none">
                  Open
                  <v-icon x-small color="primary" class="ml-1">chevron_right</v-icon>
                </span>
              </div>
            </div>
          </v-card>
        </v-col>
      </v-row>
    </section>
  </div>
</template>

<script>
import { fetchAdminStoreSettings } from '@/services/adminApi'
import { normalizeShopType, isSalonShopType, isSalonOnlyShopType } from '@/services/shopType'
import adminModuleMixin from './mixins/adminModuleMixin'

export default {
  name: 'AdminSettingsView',
  mixins: [adminModuleMixin],
  data() {
    return {
      shopKind: 'normal_store'
    }
  },
  computed: {
    merchantSlug() {
      return String(this.$route.params.merchantSlug || '').trim()
    },
    productsOosCount() {
      return (this.products || []).filter((p) => p && p.stock != null && Number(p.stock) === 0).length
    },
    settingsGroups() {
      const slug = this.merchantSlug
      const salon = isSalonShopType(this.shopKind)
      const salonOnly = isSalonOnlyShopType(this.shopKind)
      const groups = []

      const catalogue = []
      if (!salonOnly) {
        catalogue.push({
          name: 'products',
          title: 'Products',
          blurb: 'Add items, edit inventory, and remove listings.',
          icon: 'inventory_2',
          color: 'primary',
          badgeCount: this.productsOosCount,
          to: { name: 'merchant-admin-products', params: { merchantSlug: slug } }
        })
      }
      if (salon) {
        catalogue.push({
          name: 'salon-services',
          title: 'Salon services',
          blurb: 'Publish and edit bookable treatments and prices.',
          icon: 'content_cut',
          color: 'deep-purple',
          badgeCount: 0,
          to: { name: 'merchant-admin-salon', params: { merchantSlug: slug } }
        })
      }
      if (catalogue.length) {
        groups.push({ key: 'catalogue', title: 'Catalogue', items: catalogue })
      }

      groups.push({
        key: 'people',
        title: 'People',
        items: [
          {
            name: 'staff',
            title: 'Staff management',
            blurb: 'Salon bookable staff and weekly hours.',
            icon: 'groups',
            color: 'indigo',
            badgeCount: 0,
            to: { name: 'merchant-admin-salon-staff', params: { merchantSlug: slug } }
          },
          {
            name: 'team',
            title: 'Team',
            blurb: 'Staff login accounts and pay rates.',
            icon: 'badge',
            color: 'blue-grey',
            badgeCount: 0,
            to: { name: 'merchant-admin-team', params: { merchantSlug: slug } }
          },
          {
            name: 'payroll',
            title: 'Payroll',
            blurb: 'Payment calculations and mark jobs paid.',
            icon: 'account_balance_wallet',
            color: 'teal darken-1',
            badgeCount: 0,
            to: { name: 'merchant-admin-team-payroll', params: { merchantSlug: slug } }
          },
          {
            name: 'income',
            title: 'My income',
            blurb: 'Expected income from work attributed to you.',
            icon: 'savings',
            color: 'green darken-1',
            badgeCount: 0,
            to: { name: 'merchant-admin-my-income', params: { merchantSlug: slug } }
          }
        ]
      })

      const storeItems = [
        {
          name: 'store',
          title: 'Store settings',
          blurb: 'Delivery, banking, branding, contact, and business type.',
          icon: 'storefront',
          color: 'teal darken-1',
          badgeCount: 0,
          to: { name: 'merchant-admin-store', params: { merchantSlug: slug } }
        }
      ]
      if (salon) {
        storeItems.push({
          name: 'salon-payments',
          title: 'Salon payments',
          blurb: 'Recent checkout payments and export.',
          icon: 'payments',
          color: 'cyan darken-2',
          badgeCount: 0,
          to: { name: 'merchant-admin-salon-payments', params: { merchantSlug: slug } }
        })
      }
      groups.push({ key: 'store', title: 'Store', items: storeItems })

      groups.push({
        key: 'account',
        title: 'Account',
        items: [
          {
            name: 'plan',
            title: 'Plan & billing',
            blurb: 'Trial, renewals, and Peach payments.',
            icon: 'card_membership',
            color: 'deep-orange darken-1',
            badgeCount: 0,
            to: { name: 'merchant-admin-subscription', params: { merchantSlug: slug } }
          },
          {
            name: 'insights',
            title: 'Insights',
            blurb: 'Revenue, delivery fees, and top sellers.',
            icon: 'insights',
            color: 'indigo',
            badgeCount: 0,
            to: { name: 'merchant-admin-insights', params: { merchantSlug: slug } }
          },
          {
            name: 'help',
            title: 'Help',
            blurb: 'Contact platform support or open a ticket.',
            icon: 'help_outline',
            color: 'secondary',
            badgeCount: 0,
            to: { name: 'merchant-admin-help', params: { merchantSlug: slug } }
          }
        ]
      })

      return groups
    }
  },
  watch: {
    user: {
      immediate: true,
      handler(u) {
        if (u) this.loadShopKind()
      }
    },
    '$route.params.merchantSlug'() {
      if (this.user) this.loadShopKind()
    }
  },
  methods: {
    async loadShopKind() {
      try {
        const s = await fetchAdminStoreSettings(this.$route)
        this.shopKind = normalizeShopType(s && s.shopType)
      } catch {
        this.shopKind = 'normal_store'
      }
    }
  }
}
</script>

<style scoped>
.admin-settings-card {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.98);
  text-decoration: none;
  color: inherit !important;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.admin-settings-card:hover {
  border-color: rgba(234, 88, 12, 0.35);
  box-shadow: 0 10px 28px -16px rgba(15, 23, 42, 0.35) !important;
}

.min-width-0 {
  min-width: 0;
}
</style>
