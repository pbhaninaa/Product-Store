import { compareProductsByCategoryThenName } from '@/utils/productsSort'
import { fetchCatalog } from '@/services/publicStore'
import {
  cancelAdminOrder,
  confirmAdminOrderPayment,
  deleteAdminOrderPermanent,
  fetchAdminOrders,
  fetchAdminStoreSettings,
  updateAdminBanking,
  updateAdminBranding,
  updateAdminContact,
  updateAdminDelivery,
  updateAdminOpeningHours,
  createAdminProduct,
  updateAdminProduct,
  deleteAdminProduct
} from '@/services/adminApi'
import { normalizeShopType, isSalonShopType } from '@/services/shopType'
import { logout } from '@/services/auth'
import { formatZar } from '@/utils/price'
import { fetchReversePlaceLabel } from '@/utils/geocode'
import MapLocationPicker from '@/components/MapLocationPicker.vue'

/** ISO Mon=1 … Sun=7; default Mon–Fri open (matches Wheel Hub style). */
function defaultOpeningHourRowsTemplate() {
  const LABELS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
  return LABELS.map((label, i) => ({
    dayOfWeek: i + 1,
    label,
    enabled: i < 5,
    start: '09:00',
    end: '17:00'
  }))
}

export default {
  name: 'AdminModuleMixin',
  components: { MapLocationPicker },
  inject: {
    adminSession: {
      default: null
    }
  },
  data() {
    return {
      name: '',
      category: '',
      price: '',
      initialStock: '0',
      file: null,
      submitting: false,
      submitError: '',
      submitSuccess: false,
      loading: true,
      products: [],
      deletingId: null,
      deleteError: '',
      orders: [],
      ordersLoading: false,
      ordersActionError: '',
      confirmingId: null,
      orderStatusUpdatingId: null,
      unsubOrders: null,
      cancelOrderDialogOpen: false,
      cancelOrderTarget: null,
      cancellingOrderId: null,
      deleteOrderDialogOpen: false,
      deleteOrderTarget: null,
      deletingOrderId: null,
      orderCashPaymentDialogOpen: false,
      orderCashPaymentTarget: null,
      orderCashCodeInput: '',
      deleteDialogOpen: false,
      deleteTarget: null,
      inventoryError: '',
      productEditDialog: false,
      productEdit: null,
      editProductName: '',
      editProductCategory: '',
      editProductPrice: '',
      editProductStock: 0,
      editProductImageFile: null,
      editProductSaving: false,
      editProductError: '',
      editProductImgObjUrl: '',
      inventorySearch: '',
      inventoryCategoryFilter: '',
      inventoryPage: 1,
      inventoryPerPage: 4,
      inventoryPerPageOptions: [4, 6, 12, 24, 48],
      ordersPage: 1,
      ordersPerPage: 4,
      ordersPerPageOptions: [4, 5, 10, 20, 50],
      deliveryFeeDraft: '50',
      deliveryFeeModeDraft: 'standard',
      deliveryFeePerKmDraft: '8',
      storeLat: null,
      storeLng: null,
      storeNameDraft: '',
      bankNameDraft: '',
      bankAccountHolderDraft: '',
      bankAccountNumberDraft: '',
      bankBranchCodeDraft: '',
      eftNotesDraft: '',
      acceptCustomerEftDraft: true,
      acceptCustomerCashDraft: true,
      bankingSaving: false,
      bankingError: '',
      bankingSuccess: false,
      contactEmailDraft: '',
      contactPhoneDraft: '',
      contactAddressDraft: '',
      contactPageNotesDraft: '',
      contactSaving: false,
      contactError: '',
      contactSuccess: false,
      brandingLogoFile: null,
      brandingHeroFile: null,
      brandingLogoObjUrl: '',
      brandingHeroObjUrl: '',
      brandingDisplayLogoUrl: '',
      brandingDisplayHeroUrl: '',
      brandingPendingRemoveLogo: false,
      brandingPendingRemoveHero: false,
      brandingLogoInputKey: 0,
      brandingHeroInputKey: 0,
      brandingSaving: false,
      brandingError: '',
      brandingSuccess: false,
      shopTypeDraft: 'normal_store',
      openingHoursUseShopWindow: false,
      openingHoursRows: defaultOpeningHourRowsTemplate(),
      openingHoursSaving: false,
      openingHoursError: '',
      openingHoursSuccess: false,
      shopSettingsLoading: false,
      shopSettingsSaving: false,
      shopSettingsError: '',
      shopSettingsSuccess: false,
      ordersSearch: '',
      ordersDeliveryFilter: '',
      ordersPaymentMethodFilter: '',
      ordersStatusFilter: '',
      statsPreset: '30d',
      statsDateFrom: '',
      statsDateTo: '',
      statsOnlyPaid: true,
      statsDelType: '',
      statsPayMethod: '',
      statsCategoryFilter: '',
      accountGeoLat: null,
      accountGeoLng: null,
      accountGeoLoading: false,
      accountGeoError: '',
      accountGeoAutoFetched: false,
      accountStorePlaceName: '',
      accountStorePlaceLoading: false,
      accountGeoPlaceName: '',
      accountGeoPlaceLoading: false
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user ? this.adminSession.user : null
    },
    accountCardHasStorePin() {
      return Number.isFinite(this.storeLat) && Number.isFinite(this.storeLng)
    },
    brandingLogoPreview() {
      if (this.brandingLogoObjUrl) return this.brandingLogoObjUrl
      if (this.brandingPendingRemoveLogo) return ''
      return this.brandingDisplayLogoUrl || ''
    },
    brandingHeroPreview() {
      if (this.brandingHeroObjUrl) return this.brandingHeroObjUrl
      if (this.brandingPendingRemoveHero) return ''
      return this.brandingDisplayHeroUrl || ''
    },
    brandingShowRemoveLogo() {
      return Boolean(this.brandingDisplayLogoUrl || this.brandingLogoFile) && !this.brandingPendingRemoveLogo
    },
    brandingShowRemoveHero() {
      return Boolean(this.brandingDisplayHeroUrl || this.brandingHeroFile) && !this.brandingPendingRemoveHero
    },
    editProductImageDisplaySrc() {
      if (this.editProductImgObjUrl) return this.editProductImgObjUrl
      if (this.productEdit && this.productEdit.imageUrl) return this.productEdit.imageUrl
      return ''
    },
    sortedProducts() {
      return [...(this.products || [])].sort(compareProductsByCategoryThenName)
    },
    inventoryCategoryMenuItems() {
      const set = new Set()
      this.sortedProducts.forEach((p) => {
        const c = String(p.category || 'Uncategorized').trim()
        if (c) set.add(c)
      })
      const sorted = [...set].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
      return [{ text: 'All categories', value: '' }, ...sorted.map((c) => ({ text: c, value: c }))]
    },
    filteredInventoryProducts() {
      let list = [...this.sortedProducts]
      const cat = String(this.inventoryCategoryFilter || '').trim()
      if (cat) {
        const want = cat.toLowerCase()
        list = list.filter((p) => String(p.category || 'Uncategorized').trim().toLowerCase() === want)
      }
      const q = String(this.inventorySearch || '').trim().toLowerCase()
      if (q) {
        list = list.filter((p) => {
          const name = String(p.name || '').toLowerCase()
          const pcat = String(p.category || '').toLowerCase()
          return name.includes(q) || pcat.includes(q)
        })
      }
      return list
    },
    sortedOrdersForAdmin() {
      return [...(this.orders || [])].sort((a, b) => {
        const ta = new Date(a.created_at || a.createdAt).getTime()
        const tb = new Date(b.created_at || b.createdAt).getTime()
        return tb - ta
      })
    },
    ordersDeliveryFilterItems() {
      return [
        { text: 'All', value: '' },
        { text: 'Delivery', value: 'delivery' },
        { text: 'Pickup', value: 'pickup' }
      ]
    },
    ordersPaymentMethodFilterItems() {
      return [
        { text: 'All', value: '' },
        { text: 'EFT', value: 'eft' },
        { text: 'Cash in store', value: 'cash_store' }
      ]
    },
    deliveryFeeModeItems() {
      return [
        { text: 'Standard — flat fee per delivery', value: 'standard' },
        { text: 'Per kilometre — rate × distance (km)', value: 'per_km' }
      ]
    },
    shopTypeItems() {
      return [
        { text: 'Normal store — products only (default)', value: 'normal_store' },
        { text: 'Salon + store — bookings and product catalogue', value: 'salon_and_store' },
        { text: 'Salon only — bookings only (no product catalogue)', value: 'salon_only' }
      ]
    },
    /** Weekly hours are available for every store type; salons additionally use them when clipping booking slots. */
    showOpeningHoursCard() {
      return true
    },
    adminSalonBookingsEnabled() {
      return isSalonShopType(this.shopTypeDraft)
    },
    openingHoursSavedMessage() {
      return this.adminSalonBookingsEnabled
        ? 'Opening hours saved — they apply to salon booking slots when this schedule is turned on.'
        : 'Opening hours saved — customers can see them on your Contact page when this schedule is turned on.'
    },
    ordersStatusFilterItems() {
      return [
        { text: 'All', value: '' },
        { text: 'Pending payment', value: 'awaiting' },
        { text: 'Processing', value: 'processing' },
        { text: 'Ready', value: 'ready' },
        { text: 'Completed', value: 'completed' },
        { text: 'Cancelled', value: 'cancelled' }
      ]
    },
    filteredOrdersForAdmin() {
      let list = [...this.sortedOrdersForAdmin]
      const q = String(this.ordersSearch || '').trim().toLowerCase()
      if (q) {
        const qId = q.replace(/-/g, '')
        list = list.filter((o) => {
          if (String(o.customer_name || '').toLowerCase().includes(q)) return true
          if (String(o.customer_email || '').toLowerCase().includes(q)) return true
          if (String(o.delivery_address || '').toLowerCase().includes(q)) return true
          const refStr = String(o.order_ref || '').toLowerCase()
          if (refStr.includes(q)) return true
          const idStr = String(o.id || '').toLowerCase()
          if (idStr.includes(q)) return true
          if (qId.length >= 4 && idStr.replace(/-/g, '').includes(qId)) return true
          const lines = o.order_items || []
          for (let i = 0; i < lines.length; i++) {
            const it = lines[i]
            const pn = (it.products && it.products.name) || ''
            if (String(pn).toLowerCase().includes(q)) return true
          }
          return false
        })
      }
      const del = String(this.ordersDeliveryFilter || '').trim()
      if (del) list = list.filter((o) => o.delivery_type === del)
      const pm = String(this.ordersPaymentMethodFilter || '').trim()
      if (pm) list = list.filter((o) => o.payment_method === pm)
      const st = String(this.ordersStatusFilter || '').trim()
      if (st === 'cancelled') {
        list = list.filter((o) => this.orderCancelled(o))
      } else if (st === 'awaiting') {
        list = list.filter((o) => !this.orderCancelled(o) && !this.orderIsPaid(o))
      } else if (st === 'processing' || st === 'ready' || st === 'completed') {
        list = list.filter((o) => !this.orderCancelled(o) && this.effectiveOrderStatus(o) === st)
      }
      return list
    },
    inventoryPageCount() {
      const n = this.filteredInventoryProducts.length
      const per = this.inventoryPerPage
      return Math.max(1, Math.ceil(n / per) || 1)
    },
    paginatedInventoryProducts() {
      const list = this.filteredInventoryProducts
      const per = this.inventoryPerPage
      const start = (this.inventoryPage - 1) * per
      return list.slice(start, start + per)
    },
    inventoryRangeFrom() {
      const n = this.filteredInventoryProducts.length
      if (!n) return 0
      return (this.inventoryPage - 1) * this.inventoryPerPage + 1
    },
    inventoryRangeTo() {
      const n = this.filteredInventoryProducts.length
      return Math.min(this.inventoryPage * this.inventoryPerPage, n)
    },
    ordersPageCount() {
      const n = this.filteredOrdersForAdmin.length
      const per = this.ordersPerPage
      return Math.max(1, Math.ceil(n / per) || 1)
    },
    paginatedOrders() {
      const list = this.filteredOrdersForAdmin
      const per = this.ordersPerPage
      const start = (this.ordersPage - 1) * per
      return list.slice(start, start + per)
    },
    ordersRangeFrom() {
      const n = this.filteredOrdersForAdmin.length
      if (!n) return 0
      return (this.ordersPage - 1) * this.ordersPerPage + 1
    },
    ordersRangeTo() {
      const n = this.filteredOrdersForAdmin.length
      return Math.min(this.ordersPage * this.ordersPerPage, n)
    },
    paginationVisible() {
      return this.$vuetify.breakpoint.smAndDown ? 5 : 9
    },
    statsPresetItems() {
      return [
        { text: 'Last 7 days', value: '7d' },
        { text: 'Last 30 days', value: '30d' },
        { text: 'This month', value: 'month' },
        { text: 'All time', value: 'all' },
        { text: 'Custom range', value: 'custom' }
      ]
    },
    statsDelTypeItems() {
      return [
        { text: 'All', value: '' },
        { text: 'Delivery', value: 'delivery' },
        { text: 'Pickup', value: 'pickup' }
      ]
    },
    statsPayMethodItems() {
      return [
        { text: 'All', value: '' },
        { text: 'EFT', value: 'eft' },
        { text: 'Cash in store', value: 'cash_store' }
      ]
    },
    statsCategoryFilterItems() {
      const catByProductId = new Map()
      ;(this.products || []).forEach((p) => {
        catByProductId.set(p.id, String(p.category || 'Uncategorized').trim() || 'Uncategorized')
      })
      const set = new Set()
      ;(this.products || []).forEach((p) => {
        const c = String(p.category || 'Uncategorized').trim() || 'Uncategorized'
        set.add(c)
      })
      for (const o of this.statsFilteredOrders) {
        for (const it of o.order_items || []) {
          const pid = it.product_id
          if (!pid) continue
          set.add(catByProductId.get(pid) || 'Uncategorized')
        }
      }
      const sorted = [...set].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
      return [{ text: 'All categories', value: '' }, ...sorted.map((c) => ({ text: c, value: c }))]
    },
    statsDateStartMs() {
      if (!this.statsDateFrom) return null
      const d = new Date(`${this.statsDateFrom}T00:00:00`)
      const t = d.getTime()
      return Number.isFinite(t) ? t : null
    },
    statsDateEndMs() {
      if (!this.statsDateTo) return null
      const d = new Date(`${this.statsDateTo}T23:59:59.999`)
      const t = d.getTime()
      return Number.isFinite(t) ? t : null
    },
    statsFilteredOrders() {
      let list = (this.orders || []).filter((o) => !o.cancelled_at)
      if (this.statsOnlyPaid) {
        list = list.filter((o) => this.orderIsPaid(o))
      }
      const fromMs = this.statsDateStartMs
      const toMs = this.statsDateEndMs
      list = list.filter((o) => {
        const paidAt = o.payment_confirmed_at || o.paymentConfirmedAt
        const t =
          this.orderIsPaid(o) && paidAt
            ? new Date(paidAt).getTime()
            : new Date(o.created_at || o.createdAt).getTime()
        if (fromMs != null && t < fromMs) return false
        if (toMs != null && t > toMs) return false
        return true
      })
      const del = String(this.statsDelType || '').trim()
      if (del) list = list.filter((o) => o.delivery_type === del)
      const pm = String(this.statsPayMethod || '').trim()
      if (pm) list = list.filter((o) => o.payment_method === pm)
      return list
    },
    statsTotalRevenue() {
      return this.statsFilteredOrders.reduce((s, o) => s + Number(o.total_zar || 0), 0)
    },
    statsTotalSubtotal() {
      return this.statsFilteredOrders.reduce((s, o) => s + Number(o.subtotal_zar || 0), 0)
    },
    statsTotalDelivery() {
      return this.statsFilteredOrders.reduce((s, o) => s + Number(o.delivery_fee_zar || 0), 0)
    },
    statsAvgOrderValue() {
      const n = this.statsFilteredOrders.length
      if (!n) return 0
      return this.statsTotalRevenue / n
    },
    /** Per-product totals from filtered orders (all lines). Category is current catalog label for filtering. */
    statsProductAggregatesFull() {
      const catByProductId = new Map()
      ;(this.products || []).forEach((p) => {
        catByProductId.set(p.id, String(p.category || 'Uncategorized').trim() || 'Uncategorized')
      })
      const map = new Map()
      for (const o of this.statsFilteredOrders) {
        for (const it of o.order_items || []) {
          const pid = it.product_id
          if (!pid) continue
          const category = catByProductId.get(pid) || 'Uncategorized'
          const name = it.products && it.products.name ? it.products.name : 'Unknown product'
          const qty = Number(it.quantity) || 0
          const line = Number(it.line_total_zar) || 0
          const cur = map.get(pid) || { productId: pid, name, category, units: 0, revenue: 0 }
          cur.name = name
          cur.category = category
          cur.units += qty
          cur.revenue += line
          map.set(pid, cur)
        }
      }
      return Array.from(map.values())
    },
    /** Rows used for top-product tables: category filter narrows; if nothing matches, fall back to full (best sellers overall). */
    statsProductsScopedForTop() {
      const full = this.statsProductAggregatesFull
      const catFilter = String(this.statsCategoryFilter || '').trim()
      if (!catFilter) return { rows: full, fallback: false }
      const filtered = full.filter((r) => r.category === catFilter)
      if (filtered.length) return { rows: filtered, fallback: false }
      if (!full.length) return { rows: full, fallback: false }
      return { rows: full, fallback: true }
    },
    statsTopProductsCategoryFallback() {
      return this.statsProductsScopedForTop.fallback
    },
    statsProductsByUnits() {
      const rows = this.statsProductsScopedForTop.rows
      return [...rows]
        .sort((a, b) => b.units - a.units || String(a.name || '').localeCompare(String(b.name || ''), undefined, { sensitivity: 'base' }))
        .slice(0, 25)
    },
    /** #1 by units sold — e.g. 5 juice vs 2 blocks → juice. */
    statsTopSellingByUnits() {
      const list = this.statsProductsByUnits
      return list.length ? list[0] : null
    }
  },
  watch: {
    inventorySearch() {
      this.inventoryPage = 1
    },
    inventoryCategoryFilter() {
      this.inventoryPage = 1
    },
    filteredInventoryProducts(val) {
      this.$nextTick(() => {
        const max = Math.max(1, Math.ceil(val.length / this.inventoryPerPage) || 1)
        if (this.inventoryPage > max) this.inventoryPage = max
      })
    },
    inventoryPerPage() {
      this.inventoryPage = 1
    },
    ordersSearch() {
      this.ordersPage = 1
    },
    ordersDeliveryFilter() {
      this.ordersPage = 1
    },
    ordersPaymentMethodFilter() {
      this.ordersPage = 1
    },
    ordersStatusFilter() {
      this.ordersPage = 1
    },
    filteredOrdersForAdmin(val) {
      this.$nextTick(() => {
        const max = Math.max(1, Math.ceil(val.length / this.ordersPerPage) || 1)
        if (this.ordersPage > max) this.ordersPage = max
      })
    },
    ordersPerPage() {
      this.ordersPage = 1
    },
    editProductImageFile(f) {
      if (this.editProductImgObjUrl) {
        URL.revokeObjectURL(this.editProductImgObjUrl)
        this.editProductImgObjUrl = ''
      }
      if (f) {
        this.editProductImgObjUrl = URL.createObjectURL(f)
      }
    },
    deleteDialogOpen(open) {
      if (!open && !this.deletingId) {
        this.deleteTarget = null
      }
    },
    cancelOrderDialogOpen(open) {
      if (!open && !this.cancellingOrderId) {
        this.cancelOrderTarget = null
      }
    },
    deleteOrderDialogOpen(open) {
      if (!open && !this.deletingOrderId) {
        this.deleteOrderTarget = null
      }
    },
    '$route.params.merchantSlug'() {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      if (!slug) return
      this.loading = true
      fetchCatalog(slug)
        .then((products) => {
          this.products = products
        })
        .catch(() => {
          this.products = []
        })
        .finally(() => {
          this.loading = false
          try {
            this.$root.$emit('merchant-admin-badges-refresh')
          } catch {
            // ignore
          }
        })
    },
    'adminSession.user': {
      immediate: true,
      handler(u) {
        if (u) {
          this.ordersLoading = true
          fetchAdminOrders(this.$route)
            .then((res) => {
              this.orders = res && res.orders ? res.orders : []
            })
            .catch(() => {
              this.orders = []
            })
            .finally(() => {
              this.ordersLoading = false
              try {
                this.$root.$emit('merchant-admin-badges-refresh')
              } catch {
                // ignore
              }
            })
          this.loadShopSettingsForAdmin()
        } else {
          this.orders = []
          this.ordersLoading = false
          this.shopSettingsError = ''
          this.shopSettingsSuccess = false
          this.accountGeoLat = null
          this.accountGeoLng = null
          this.accountGeoError = ''
          this.accountGeoLoading = false
          this.accountGeoAutoFetched = false
          this.accountStorePlaceName = ''
          this.accountGeoPlaceName = ''
        }
      }
    }
  },
  created() {
    this.loading = true
    const slug = String(this.$route.params.merchantSlug || '').trim()
    fetchCatalog(slug)
      .then((products) => {
        this.products = products
      })
      .catch(() => {
        this.products = []
      })
      .finally(() => {
        this.loading = false
        try {
          this.$root.$emit('merchant-admin-badges-refresh')
        } catch {
          // ignore
        }
      })
    this.applyStatsPreset('30d')
  },
  beforeDestroy() {
    if (this._accountStorePlaceTimer) clearTimeout(this._accountStorePlaceTimer)
    if (this.unsubOrders) this.unsubOrders()
    if (this.brandingLogoObjUrl) URL.revokeObjectURL(this.brandingLogoObjUrl)
    if (this.brandingHeroObjUrl) URL.revokeObjectURL(this.brandingHeroObjUrl)
    if (this.editProductImgObjUrl) URL.revokeObjectURL(this.editProductImgObjUrl)
  },
  methods: {
    displayOrderRef(o) {
      return String((o && o.id) || '')
    },
    formatZar,
    formatWhen(iso) {
      if (!iso) return ''
      try {
        return new Date(iso).toLocaleString()
      } catch {
        return String(iso)
      }
    },
    lineProductName(it) {
      if (it.products && it.products.name) return it.products.name
      return 'Product'
    },
    orderCancelled(o) {
      return Boolean(o && o.status === 'cancelled')
    },
    /** Cancel / permanent delete allowed only while payment is still pending. */
    orderIsStrictlyPending(o) {
      if (!o || this.orderCancelled(o)) return false
      return String(o.status || '').toLowerCase() === 'pending_payment'
    },
    /** Paid in platform API is {@code status === 'paid'}; tolerate legacy {@code payment_confirmed} flags. */
    orderIsPaid(o) {
      if (!o) return false
      const st = String(o.status || '').toLowerCase()
      return st === 'paid' || Boolean(o.payment_confirmed || o.paymentConfirmed)
    },
    orderPaymentVerificationState(o) {
      return String(o.paymentVerificationState || o.payment_verification_state || '').toLowerCase()
    },
    /**
     * Merchant confirms payment: EFT after manual review queue; cash after customer pays in store (code entry).
     */
    showMerchantConfirmOrderPayment(o) {
      if (this.orderIsPaid(o) || this.orderCancelled(o)) return false
      const pm = String(o.payment_method || o.paymentMethod || '').toLowerCase()
      if (pm === 'cash_store') return this.orderIsStrictlyPending(o)
      if (pm === 'eft') return this.orderPaymentVerificationState(o) === 'manual_pending'
      return false
    },
    async refreshAdminOrders() {
      if (!this.adminSession || !this.adminSession.user) return
      try {
        const res = await fetchAdminOrders(this.$route)
        this.orders = res && res.orders ? res.orders : []
      } catch {
        // keep existing list
      } finally {
        try {
          this.$root.$emit('merchant-admin-badges-refresh')
        } catch {
          // ignore
        }
      }
    },
    effectiveOrderStatus(o) {
      if (!o) return 'awaiting_payment'
      if (o.status === 'cancelled') return 'cancelled'
      if (o.status === 'paid') return 'processing'
      return 'awaiting_payment'
    },
    fulfillmentStatusLabel(o) {
      const s = this.effectiveOrderStatus(o)
      const d = o && o.delivery_type === 'delivery'
      const map = {
        cancelled: 'Cancelled',
        awaiting_payment: 'Pending',
        processing: 'Processing',
        ready: d ? 'Out for delivery' : 'Ready for pickup',
        completed: 'Completed'
      }
      return map[s] || s
    },
    fulfillmentChipColor(o) {
      const s = this.effectiveOrderStatus(o)
      if (s === 'completed') return 'success'
      if (s === 'ready') return 'accent'
      if (s === 'processing') return 'primary'
      return 'grey'
    },
    orderFulfillmentSelectItems(o) {
      const d = o && o.delivery_type === 'delivery'
      return [
        { text: 'Processing', value: 'processing' },
        { text: d ? 'Out for delivery' : 'Ready for pickup', value: 'ready' },
        { text: 'Completed', value: 'completed' }
      ]
    },
    async setOrderFulfillmentStatus() {
      // Fulfillment statuses not implemented yet in backend.
      return
    },
    applyStatsPreset(p) {
      const pad = (n) => String(n).padStart(2, '0')
      const fmt = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
      const today = new Date()
      this.statsPreset = p
      if (p === 'all') {
        this.statsDateFrom = ''
        this.statsDateTo = ''
        return
      }
      if (p === 'custom') return
      const start = new Date(today)
      if (p === '7d') start.setDate(start.getDate() - 7)
      else if (p === '30d') start.setDate(start.getDate() - 30)
      else if (p === 'month') {
        start.setFullYear(today.getFullYear(), today.getMonth(), 1)
      }
      this.statsDateFrom = fmt(start)
      this.statsDateTo = fmt(today)
    },
    onStatsPresetChange(val) {
      if (val && val !== 'custom') this.applyStatsPreset(val)
    },
    bumpStatsToCustom() {
      this.statsPreset = 'custom'
    },
    openCancelOrderDialog(o) {
      this.cancelOrderTarget = o
      this.cancelOrderDialogOpen = true
    },
    async confirmCancelOrder() {
      if (!this.cancelOrderTarget || !this.cancelOrderTarget.id) return
      this.ordersActionError = ''
      this.cancellingOrderId = this.cancelOrderTarget.id
      try {
        await cancelAdminOrder(this.$route, this.cancelOrderTarget.id)
        this.cancelOrderDialogOpen = false
        this.cancelOrderTarget = null
        await this.refreshAdminOrders()
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not cancel order.'
      } finally {
        this.cancellingOrderId = null
      }
    },
    openDeleteOrderDialog(o) {
      if (!o || !o.id) return
      if (!this.orderIsStrictlyPending(o)) return
      this.ordersActionError = ''
      this.deleteOrderTarget = o
      this.deleteOrderDialogOpen = true
    },
    closeDeleteOrderDialogIfIdle() {
      if (this.deletingOrderId) return
      this.deleteOrderDialogOpen = false
      this.deleteOrderTarget = null
    },
    async confirmDeleteOrderPermanent() {
      if (!this.deleteOrderTarget || !this.deleteOrderTarget.id) return
      if (!this.orderIsStrictlyPending(this.deleteOrderTarget)) {
        this.ordersActionError = 'Only orders that are still pending payment can be deleted.'
        this.deleteOrderDialogOpen = false
        this.deleteOrderTarget = null
        return
      }
      this.ordersActionError = ''
      this.deletingOrderId = this.deleteOrderTarget.id
      try {
        const res = await deleteAdminOrderPermanent(this.$route, this.deleteOrderTarget.id)
        if (!res || res.ok !== true) {
          this.ordersActionError =
            (res && res.reason === 'not_deletable'
              ? 'This order cannot be deleted (only unpaid orders may be removed).'
              : null) || 'Could not delete order.'
          return
        }
        this.deleteOrderDialogOpen = false
        this.deleteOrderTarget = null
        await this.refreshAdminOrders()
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not delete order.'
      } finally {
        this.deletingOrderId = null
      }
    },
    openInvoicePrint(o) {
      if (!o || !o.id) return
      const r = this.$router.resolve({
        name: 'merchant-admin-order-invoice',
        params: { merchantSlug: String(this.$route.params.merchantSlug || '').trim(), orderId: o.id }
      })
      window.open(r.href, '_blank', 'noopener,noreferrer')
    },
    closeOrderCashPaymentDialogIfIdle() {
      if (this.confirmingId) return
      this.ordersActionError = ''
      this.orderCashPaymentDialogOpen = false
      this.orderCashPaymentTarget = null
      this.orderCashCodeInput = ''
    },
    async confirmPayment(o) {
      if (!o || !o.id) return
      const pm = String(o.payment_method || o.paymentMethod || '').toLowerCase()
      if (pm === 'cash_store') {
        this.ordersActionError = ''
        this.orderCashPaymentTarget = o
        this.orderCashCodeInput = ''
        this.orderCashPaymentDialogOpen = true
        return
      }
      this.ordersActionError = ''
      this.confirmingId = o.id
      try {
        const res = await confirmAdminOrderPayment(this.$route, o.id)
        if (!res || res.ok !== true) {
          this.ordersActionError =
            'Could not confirm payment. For EFT, the customer must upload proof first; if their reference did not auto-match, confirm only after you verify their transfer in your bank.'
        } else {
          await this.refreshAdminOrders()
        }
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not confirm.'
      } finally {
        this.confirmingId = null
      }
    },
    async submitOrderCashPaymentConfirm() {
      const o = this.orderCashPaymentTarget
      if (!o || !o.id) return
      const code = String(this.orderCashCodeInput || '').trim()
      if (!code) {
        this.ordersActionError = 'Enter the payment code the customer was given at checkout.'
        return
      }
      this.ordersActionError = ''
      this.confirmingId = o.id
      try {
        const res = await confirmAdminOrderPayment(this.$route, o.id, { cashCode: code })
        if (!res || res.ok !== true) {
          this.ordersActionError =
            'Invalid code or this order is no longer awaiting cash confirmation. Check the code and order status.'
          return
        }
        this.orderCashPaymentDialogOpen = false
        this.orderCashPaymentTarget = null
        this.orderCashCodeInput = ''
        await this.refreshAdminOrders()
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not confirm.'
      } finally {
        this.confirmingId = null
      }
    },
    async loadShopSettingsForAdmin() {
      this.shopSettingsLoading = true
      this.shopSettingsError = ''
      try {
        const s = await fetchAdminStoreSettings(this.$route)
        const flatFromApi =
          Number.isFinite(Number(s && s.deliveryFeeZar)) ? Number(s.deliveryFeeZar) : NaN
        const flatFromEntity =
          Number.isFinite(Number(s && s.deliveryFeeFlatZar)) ? Number(s.deliveryFeeFlatZar) : NaN
        this.deliveryFeeDraft = String(
          Number.isFinite(flatFromApi) ? flatFromApi : Number.isFinite(flatFromEntity) ? flatFromEntity : 50
        )
        this.deliveryFeeModeDraft =
          (s && s.deliveryFeeMode) === 'per_km' ? 'per_km' : 'standard'
        const perKm = Number.isFinite(Number(s && s.deliveryFeePerKmZar))
          ? Number(s.deliveryFeePerKmZar)
          : 8
        this.deliveryFeePerKmDraft = String(perKm)
        this.shopTypeDraft = normalizeShopType(s.shopType)
        this.applyOpeningHoursFromSettings(s)
        this.storeLat = Number.isFinite(s.storeLat) ? s.storeLat : null
        this.storeLng = Number.isFinite(s.storeLng) ? s.storeLng : null
        this.storeNameDraft = s.storeName || ''
        this.bankNameDraft = s.bankName || ''
        this.bankAccountHolderDraft = s.bankAccountHolder || ''
        this.bankAccountNumberDraft = s.bankAccountNumber || ''
        this.bankBranchCodeDraft = s.bankBranchCode || ''
        this.eftNotesDraft = s.eftBankInstructions || ''
        this.acceptCustomerEftDraft = s.acceptCustomerEft !== false
        this.acceptCustomerCashDraft = s.acceptCustomerCash !== false
        this.contactEmailDraft = s.contactEmail || ''
        this.contactPhoneDraft = s.contactPhone || ''
        this.contactAddressDraft = s.contactAddress || ''
        this.contactPageNotesDraft = s.contactNotes || ''
        this.brandingDisplayLogoUrl = s.storeLogoUrl || ''
        this.brandingDisplayHeroUrl = s.storeHeroUrl || ''
      } catch (e) {
        this.shopSettingsError = e && e.message ? e.message : 'Could not load delivery fee.'
      } finally {
        this.shopSettingsLoading = false
        this.$root.$emit('merchant-shop-meta-updated', {
          shopType: this.shopTypeDraft
        })
        this.$nextTick(() => {
          if (this.accountCardHasStorePin) {
            this._loadAccountStorePlaceName()
          } else {
            this.accountStorePlaceName = ''
          }
          if (
            this.user &&
            !this.accountCardHasStorePin &&
            !this.accountGeoAutoFetched
          ) {
            this.accountGeoAutoFetched = true
            this.fetchAccountCurrentLocation()
          }
        })
      }
    },
    _loadAccountStorePlaceName() {
      if (!this.accountCardHasStorePin) {
        this.accountStorePlaceName = ''
        this.accountStorePlaceLoading = false
        return
      }
      this.accountStorePlaceLoading = true
      fetchReversePlaceLabel(this.storeLat, this.storeLng).then((label) => {
        this.accountStorePlaceName = label
        this.accountStorePlaceLoading = false
      })
    },
    scheduleAccountStorePlaceName() {
      if (this._accountStorePlaceTimer) clearTimeout(this._accountStorePlaceTimer)
      this._accountStorePlaceTimer = setTimeout(() => {
        this._accountStorePlaceTimer = null
        this._loadAccountStorePlaceName()
      }, 500)
    },
    _loadAccountGeoPlaceName() {
      if (this.accountCardHasStorePin) return
      if (this.accountGeoLat == null || this.accountGeoLng == null) {
        this.accountGeoPlaceName = ''
        this.accountGeoPlaceLoading = false
        return
      }
      this.accountGeoPlaceLoading = true
      fetchReversePlaceLabel(this.accountGeoLat, this.accountGeoLng).then((label) => {
        this.accountGeoPlaceName = label
        this.accountGeoPlaceLoading = false
      })
    },
    formatAccountCoord(n) {
      if (n == null || !Number.isFinite(Number(n))) return '—'
      return Number(n).toFixed(5)
    },
    accountMapsUrl(lat, lng) {
      const la = Number(lat)
      const ln = Number(lng)
      if (!Number.isFinite(la) || !Number.isFinite(ln)) return '#'
      return `https://www.google.com/maps?q=${la},${ln}`
    },
    fetchAccountCurrentLocation() {
      if (!navigator.geolocation) {
        this.accountGeoError = 'Location is not available in this browser.'
        return
      }
      this.accountGeoLoading = true
      this.accountGeoError = ''
      this.accountGeoPlaceName = ''
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          this.accountGeoLat = pos.coords.latitude
          this.accountGeoLng = pos.coords.longitude
          this.accountGeoLoading = false
          this._loadAccountGeoPlaceName()
        },
        (err) => {
          this.accountGeoLoading = false
          this.accountGeoError =
            err && err.code === 1
              ? 'Location permission denied — allow location or set the store pin under Shop & delivery.'
              : 'Could not read your location. Try again or set the store pin on the map.'
        },
        { enableHighAccuracy: false, maximumAge: 60000, timeout: 15000 }
      )
    },
    onStoreMapInput({ lat, lng }) {
      this.storeLat = lat
      this.storeLng = lng
      this.scheduleAccountStorePlaceName()
    },
    async refetchMerchantProducts() {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      if (!slug) return
      try {
        this.products = await fetchCatalog(slug)
      } catch {
        //
      } finally {
        try {
          this.$root.$emit('merchant-admin-badges-refresh')
        } catch {
          // ignore
        }
      }
    },
    async saveShopSettings() {
      this.shopSettingsError = ''
      this.shopSettingsSuccess = false
      if (this.deliveryFeeModeDraft === 'per_km' && (this.storeLat == null || this.storeLng == null)) {
        this.shopSettingsError =
          'Per-km pricing needs the store location — place the pin on the store map (Shop & delivery section).'
        return
      }
      this.shopSettingsSaving = true
      try {
        await updateAdminDelivery(this.$route, {
          deliveryFeeZar: this.deliveryFeeDraft,
          deliveryFeeMode: this.deliveryFeeModeDraft,
          deliveryFeePerKmZar: this.deliveryFeePerKmDraft,
          storeLat: this.deliveryFeeModeDraft === 'per_km' ? this.storeLat : null,
          storeLng: this.deliveryFeeModeDraft === 'per_km' ? this.storeLng : null
        })
        this.shopSettingsSuccess = true
        setTimeout(() => {
          this.shopSettingsSuccess = false
        }, 3500)
      } catch (e) {
        this.shopSettingsError = e && e.message ? e.message : 'Could not save delivery fee.'
      } finally {
        this.shopSettingsSaving = false
      }
    },
    onBrandingLogoChange() {
      if (this.brandingLogoObjUrl) {
        URL.revokeObjectURL(this.brandingLogoObjUrl)
        this.brandingLogoObjUrl = ''
      }
      this.brandingPendingRemoveLogo = false
      const file = this.brandingLogoFile
      if (file) {
        this.brandingLogoObjUrl = URL.createObjectURL(file)
      }
    },
    onBrandingHeroChange() {
      if (this.brandingHeroObjUrl) {
        URL.revokeObjectURL(this.brandingHeroObjUrl)
        this.brandingHeroObjUrl = ''
      }
      this.brandingPendingRemoveHero = false
      const file = this.brandingHeroFile
      if (file) {
        this.brandingHeroObjUrl = URL.createObjectURL(file)
      }
    },
    clearBrandingLogoIntent() {
      this.brandingLogoFile = null
      if (this.brandingLogoObjUrl) {
        URL.revokeObjectURL(this.brandingLogoObjUrl)
        this.brandingLogoObjUrl = ''
      }
      this.brandingPendingRemoveLogo = true
      this.brandingLogoInputKey += 1
    },
    clearBrandingHeroIntent() {
      this.brandingHeroFile = null
      if (this.brandingHeroObjUrl) {
        URL.revokeObjectURL(this.brandingHeroObjUrl)
        this.brandingHeroObjUrl = ''
      }
      this.brandingPendingRemoveHero = true
      this.brandingHeroInputKey += 1
    },
    async saveStoreBranding() {
      this.brandingError = ''
      this.brandingSuccess = false
      this.brandingSaving = true
      try {
        await updateAdminBranding(this.$route, {
          storeName: this.storeNameDraft,
          shopType: this.shopTypeDraft,
          logoFile: this.brandingLogoFile,
          heroFile: this.brandingHeroFile,
          removeLogo: this.brandingPendingRemoveLogo,
          removeHero: this.brandingPendingRemoveHero
        })
        if (this.brandingLogoObjUrl) {
          URL.revokeObjectURL(this.brandingLogoObjUrl)
          this.brandingLogoObjUrl = ''
        }
        if (this.brandingHeroObjUrl) {
          URL.revokeObjectURL(this.brandingHeroObjUrl)
          this.brandingHeroObjUrl = ''
        }
        this.brandingLogoFile = null
        this.brandingHeroFile = null
        this.brandingPendingRemoveLogo = false
        this.brandingPendingRemoveHero = false
        this.brandingLogoInputKey += 1
        this.brandingHeroInputKey += 1
        await this.loadShopSettingsForAdmin()
        this.brandingSuccess = true
        this.$root.$emit('shop-settings-updated')
        this.$root.$emit('merchant-shop-meta-updated', {
          shopType: this.shopTypeDraft
        })
        setTimeout(() => {
          this.brandingSuccess = false
        }, 3500)
      } catch (e) {
        this.brandingError = e && e.message ? e.message : 'Could not save store branding.'
      } finally {
        this.brandingSaving = false
      }
    },
    async saveBankingSettings() {
      this.bankingError = ''
      this.bankingSuccess = false
      this.bankingSaving = true
      try {
        await updateAdminBanking(this.$route, {
          bankName: this.bankNameDraft,
          bankAccountHolder: this.bankAccountHolderDraft,
          bankAccountNumber: this.bankAccountNumberDraft,
          bankBranchCode: this.bankBranchCodeDraft,
          eftBankInstructions: this.eftNotesDraft,
          acceptCustomerEft: Boolean(this.acceptCustomerEftDraft),
          acceptCustomerCash: Boolean(this.acceptCustomerCashDraft)
        })
        this.bankingSuccess = true
        setTimeout(() => {
          this.bankingSuccess = false
        }, 3500)
      } catch (e) {
        this.bankingError = e && e.message ? e.message : 'Could not save banking details.'
      } finally {
        this.bankingSaving = false
      }
    },
    async saveContactSettings() {
      this.contactError = ''
      this.contactSuccess = false
      this.contactSaving = true
      try {
        await updateAdminContact(this.$route, {
          contactEmail: this.contactEmailDraft,
          contactPhone: this.contactPhoneDraft,
          contactAddress: this.contactAddressDraft,
          contactNotes: this.contactPageNotesDraft
        })
        this.contactSuccess = true
        setTimeout(() => {
          this.contactSuccess = false
        }, 3500)
      } catch (e) {
        this.contactError = e && e.message ? e.message : 'Could not save contact details.'
      } finally {
        this.contactSaving = false
      }
    },
    defaultOpeningHourRows() {
      return defaultOpeningHourRowsTemplate().map((r) => ({ ...r }))
    },
    applyOpeningHoursFromSettings(s) {
      const raw = s && s.openingHoursJson
      const t = raw == null ? '' : String(raw).trim()
      if (!t || t === '[]' || t === '{}') {
        this.openingHoursUseShopWindow = false
        this.openingHoursRows = this.defaultOpeningHourRows()
        return
      }
      try {
        const arr = JSON.parse(t)
        if (!Array.isArray(arr) || !arr.length) {
          this.openingHoursUseShopWindow = false
          this.openingHoursRows = this.defaultOpeningHourRows()
          return
        }
        this.openingHoursUseShopWindow = true
        const base = this.defaultOpeningHourRows()
        const byDow = {}
        arr.forEach((node) => {
          if (!node || typeof node !== 'object') return
          const dow = Number(node.dayOfWeek)
          if (!Number.isInteger(dow) || dow < 1 || dow > 7) return
          if (node.closed === true) {
            byDow[dow] = { enabled: false, start: '09:00', end: '17:00' }
          } else if (node.start != null && node.end != null) {
            byDow[dow] = {
              enabled: true,
              start: String(node.start).trim(),
              end: String(node.end).trim()
            }
          }
        })
        this.openingHoursRows = base.map((r) => {
          const hit = byDow[r.dayOfWeek]
          if (!hit) return { ...r, enabled: false }
          return { ...r, enabled: hit.enabled, start: hit.start, end: hit.end }
        })
      } catch {
        this.openingHoursUseShopWindow = false
        this.openingHoursRows = this.defaultOpeningHourRows()
      }
    },
    serializeOpeningHoursJson() {
      if (!this.openingHoursUseShopWindow) return '[]'
      return JSON.stringify(
        this.openingHoursRows.map((r) =>
          r.enabled ? { dayOfWeek: r.dayOfWeek, start: r.start, end: r.end } : { dayOfWeek: r.dayOfWeek, closed: true }
        )
      )
    },
    async saveOpeningHours() {
      this.openingHoursError = ''
      this.openingHoursSuccess = false
      this.openingHoursSaving = true
      try {
        await updateAdminOpeningHours(this.$route, this.serializeOpeningHoursJson())
        await this.loadShopSettingsForAdmin()
        this.openingHoursSuccess = true
        setTimeout(() => {
          this.openingHoursSuccess = false
        }, 3500)
      } catch (e) {
        this.openingHoursError = e && e.message ? e.message : 'Could not save opening hours.'
      } finally {
        this.openingHoursSaving = false
      }
    },
    async doLogout() {
      const slugRaw =
        this.$route && this.$route.params ? String(this.$route.params.merchantSlug || '').trim() : ''
      const slug = slugRaw || 'demo'
      await logout()
      await this.$router.replace({ name: 'merchant-home', params: { merchantSlug: slug } }).catch(() => {})
    },
    async submit() {
      this.submitError = ''
      this.submitSuccess = false
      this.submitting = true

      try {
        await createAdminProduct(this.$route, {
          name: this.name,
          category: this.category,
          price: this.price,
          stock: this.initialStock,
          file: this.file
        })
        await this.refetchMerchantProducts()
        this.name = ''
        this.category = ''
        this.price = ''
        this.initialStock = '0'
        this.file = null
        this.submitSuccess = true
        setTimeout(() => (this.submitSuccess = false), 2500)
      } catch (e) {
        this.submitError = e && e.message ? e.message : 'Upload failed.'
      } finally {
        this.submitting = false
      }
    },
    openProductEdit(p) {
      if (!p || !p.id || !this.user) return
      this.editProductError = ''
      this.productEdit = { ...p }
      this.editProductName = String(p.name || '')
      this.editProductCategory =
        p.category != null && String(p.category).trim() !== '' ? String(p.category).trim() : 'Uncategorized'
      this.editProductPrice = p.price != null ? String(p.price) : ''
      const s = p.stock != null ? p.stock : 0
      this.editProductStock = Number.isFinite(Number(s)) ? Math.max(0, Math.floor(Number(s))) : 0
      this.editProductImageFile = null
      if (this.editProductImgObjUrl) {
        URL.revokeObjectURL(this.editProductImgObjUrl)
        this.editProductImgObjUrl = ''
      }
      this.productEditDialog = true
    },
    closeProductEditIfIdle() {
      if (this.editProductSaving) return
      this.productEditDialog = false
      this.productEdit = null
      this.editProductImageFile = null
      if (this.editProductImgObjUrl) {
        URL.revokeObjectURL(this.editProductImgObjUrl)
        this.editProductImgObjUrl = ''
      }
    },
    async saveProductEdit() {
      if (!this.productEdit || !this.productEdit.id) return
      this.editProductError = ''
      this.editProductSaving = true
      try {
        await updateAdminProduct(this.$route, this.productEdit.id, {
          name: this.editProductName,
          category: this.editProductCategory,
          price: this.editProductPrice,
          stock: this.editProductStock,
          imageFile: this.editProductImageFile || null,
          previousImagePath: this.productEdit.imagePath || null
        })
        await this.refetchMerchantProducts()
        this.productEditDialog = false
        this.productEdit = null
        this.editProductImageFile = null
        if (this.editProductImgObjUrl) {
          URL.revokeObjectURL(this.editProductImgObjUrl)
          this.editProductImgObjUrl = ''
        }
      } catch (e) {
        this.editProductError = e && e.message ? e.message : 'Could not save product.'
      } finally {
        this.editProductSaving = false
      }
    },
    openDeleteConfirm(p) {
      if (!this.user) return
      this.deleteError = ''
      this.deleteTarget = p
      this.deleteDialogOpen = true
    },
    closeDeleteDialogIfIdle() {
      if (this.deletingId) return
      this.deleteDialogOpen = false
      this.deleteTarget = null
    },
    async confirmDeleteProduct() {
      const p = this.deleteTarget
      if (!this.user || !p) return
      this.deleteError = ''
      const removedId = p.id
      this.deletingId = removedId
      try {
        await deleteAdminProduct(this.$route, removedId)
        this.deleteError = ''
        this.deleteDialogOpen = false
        this.deleteTarget = null
        await this.refetchMerchantProducts()
      } catch (e) {
        this.deleteError = e && e.message ? e.message : 'Delete failed.'
      } finally {
        this.deletingId = null
      }
    }
  }
}