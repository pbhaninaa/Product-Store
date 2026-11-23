<template>
  <div v-if="user">
    <v-card class="admin-card pa-4 pa-sm-6 mb-4" elevation="3" rounded="xl">
      <div class="card-label mb-2">Settings</div>
      <p class="text-body-2 text--secondary mb-0">
        <template v-if="staffOnly">
          Your income, the plan status, and the areas the store owner has given you access to.
        </template>
        <template v-else>
          Set up your catalogue, team, store details, and billing. Everyday work (orders, bookings, alerts) stays in
          the tabs above.
        </template>
      </p>
    </v-card>

    <section v-for="group in settingsGroups" :key="group.key" class="mb-6">
      <div class="card-label mb-2">{{ group.title }}</div>
      <v-row dense class="admin-card-row">
        <v-col v-for="item in group.items" :key="item.name" cols="12" sm="6" lg="4">
          <v-card
            :to="item.to"
            class="admin-link-tile admin-eq-card pa-4 rounded-xl"
            elevation="2"
            hover
            ripple
          >
            <div class="admin-link-tile__inner">
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
              <div class="admin-link-tile__text">
                <div class="text-subtitle-1 font-weight-bold mb-1 admin-clamp-1">{{ item.title }}</div>
                <p class="text-body-2 text--secondary mb-2 admin-clamp-2">{{ item.blurb }}</p>
                <span class="admin-link-tile__open text-caption font-weight-bold primary--text text-none">
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
      const staff = this.staffOnly
      const can = (p) => this.can(p)
      const groups = []

      const catalogue = []
      if (!salonOnly && can('PRODUCTS')) {
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
      if (salon && can('SERVICES')) {
        catalogue.push({
          name: 'salon-services',
          title: 'Services',
          blurb: 'Publish and edit bookable services and prices.',
          icon: 'event_available',
          color: 'deep-purple',
          badgeCount: 0,
          to: { name: 'merchant-admin-salon', params: { merchantSlug: slug } }
        })
      }
      if (catalogue.length) {
        groups.push({ key: 'catalogue', title: 'Catalogue', items: catalogue })
      }

      const people = []
      if (salon && can('SERVICES')) {
        people.push({
          name: 'staff',
          title: 'Staff management',
          blurb: 'Bookable staff and weekly hours.',
          icon: 'groups',
          color: 'indigo',
          badgeCount: 0,
          to: { name: 'merchant-admin-salon-staff', params: { merchantSlug: slug } }
        })
      }
      if (!staff) {
        people.push({
          name: 'team',
          title: 'Login team',
          blurb: 'Staff login accounts, pay rates, and permissions.',
          icon: 'badge',
          color: 'blue-grey',
          badgeCount: 0,
          to: { name: 'merchant-admin-team', params: { merchantSlug: slug } }
        })
      }
      if (can('TEAM_PAYROLL')) {
        people.push({
          name: 'payroll',
          title: 'Payroll',
          blurb: 'Payment calculations and mark jobs paid.',
          icon: 'account_balance_wallet',
          color: 'teal darken-1',
          badgeCount: 0,
          to: { name: 'merchant-admin-team', params: { merchantSlug: slug }, query: { tab: 'payroll' } }
        })
      }
      people.push({
        name: 'income',
        title: 'My income',
        blurb: 'Expected income from work attributed to you.',
        icon: 'savings',
        color: 'green darken-1',
        badgeCount: 0,
        to: { name: 'merchant-admin-team', params: { merchantSlug: slug }, query: { tab: 'income' } }
      })
      groups.push({ key: 'people', title: 'People', items: people })

      const storeItems = []
      if (can('STORE_SETTINGS')) {
        storeItems.push({
          name: 'store',
          title: 'Store settings',
          blurb: 'Delivery, banking, branding, contact, and business type.',
          icon: 'storefront',
          color: 'teal darken-1',
          badgeCount: 0,
          to: { name: 'merchant-admin-store', params: { merchantSlug: slug } }
        })
      }
      if (salon && can('PAYMENTS')) {
        storeItems.push({
          name: 'salon-payments',
          title: 'Payments',
          blurb: 'Recent checkout payments and export.',
          icon: 'payments',
          color: 'cyan darken-2',
          badgeCount: 0,
          to: { name: 'merchant-admin-salon-payments', params: { merchantSlug: slug } }
        })
      }
      if (storeItems.length) {
        groups.push({ key: 'store', title: 'Store', items: storeItems })
      }

      const account = [
        {
          name: 'plan',
          title: 'Plan & billing',
          blurb: staff ? 'See the store’s current plan status.' : 'Trial, renewals, and online payments.',
          icon: 'card_membership',
          color: 'deep-orange darken-1',
          badgeCount: 0,
          to: { name: 'merchant-admin-subscription', params: { merchantSlug: slug } }
        }
      ]
      if (can('INSIGHTS')) {
        account.push({
          name: 'insights',
          title: 'Insights',
          blurb: 'Revenue, delivery fees, and top sellers.',
          icon: 'insights',
          color: 'indigo',
          badgeCount: 0,
          to: { name: 'merchant-admin-insights', params: { merchantSlug: slug } }
        })
      }
      account.push({
        name: 'help',
        title: 'Help',
        blurb: 'Contact platform support or open a ticket.',
        icon: 'help_outline',
        color: 'secondary',
        badgeCount: 0,
        to: { name: 'merchant-admin-help', params: { merchantSlug: slug } }
      })
      groups.push({ key: 'account', title: 'Account', items: account })

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
