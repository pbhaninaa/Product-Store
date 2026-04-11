import {
  compareProductsByCategoryThenName,
  createProduct,
  deleteProduct,
  subscribeToProducts,
  updateProduct
} from '@/services/products'
import {
  cancelUnpaidOrder,
  confirmOrderPayment,
  deleteOrderPermanently,
  fetchShopSettings,
  orderDisplayRef,
  subscribeToOrders,
  updateBankingDetails,
  updateContactDetails,
  updateOrderStatus,
  updateShopSettings,
  updateStoreBranding
} from '@/services/orders'
import { logout } from '@/services/auth'
import { formatZar } from '@/utils/price'
import { fetchReversePlaceLabel } from '@/utils/geocode'
import MapLocationPicker from '@/components/MapLocationPicker.vue'

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
        const ta = new Date(a.created_at).getTime()
        const tb = new Date(b.created_at).getTime()
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
    ordersStatusFilterItems() {
      return [
        { text: 'All', value: '' },
        { text: 'Awaiting payment', value: 'awaiting' },
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
        list = list.filter((o) => !this.orderCancelled(o) && !o.payment_confirmed)
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
        list = list.filter((o) => o.payment_confirmed)
      }
      const fromMs = this.statsDateStartMs
      const toMs = this.statsDateEndMs
      list = list.filter((o) => {
        const t =
          o.payment_confirmed && o.payment_confirmed_at
            ? new Date(o.payment_confirmed_at).getTime()
            : new Date(o.created_at).getTime()
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
    'adminSession.user': {
      immediate: true,
      handler(u) {
        if (this.unsubOrders) {
          this.unsubOrders()
          this.unsubOrders = null
        }
        if (u) {
          this.ordersLoading = true
          this.unsubOrders = subscribeToOrders((rows) => {
            this.orders = rows
            this.ordersLoading = false
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
    this.unsub = subscribeToProducts((products) => {
      this.products = products
      this.loading = false
    })
    this.applyStatsPreset('30d')
  },
  beforeDestroy() {
    if (this._accountStorePlaceTimer) clearTimeout(this._accountStorePlaceTimer)
    if (this.unsub) this.unsub()
    if (this.unsubOrders) this.unsubOrders()
    if (this.brandingLogoObjUrl) URL.revokeObjectURL(this.brandingLogoObjUrl)
    if (this.brandingHeroObjUrl) URL.revokeObjectURL(this.brandingHeroObjUrl)
    if (this.editProductImgObjUrl) URL.revokeObjectURL(this.editProductImgObjUrl)
  },
  methods: {
    displayOrderRef(o) {
      return orderDisplayRef(o)
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
      return Boolean(o && o.cancelled_at)
    },
    effectiveOrderStatus(o) {
      if (!o) return 'awaiting_payment'
      if (o.cancelled_at) return 'cancelled'
      const s = String(o.order_status || '').trim()
      if (s) return s
      return o.payment_confirmed ? 'processing' : 'awaiting_payment'
    },
    fulfillmentStatusLabel(o) {
      const s = this.effectiveOrderStatus(o)
      const d = o && o.delivery_type === 'delivery'
      const map = {
        cancelled: 'Cancelled',
        awaiting_payment: 'Awaiting payment',
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
    async setOrderFulfillmentStatus(o, status) {
      if (!o || !o.id) return
      const next = String(status || '').trim()
      if (this.effectiveOrderStatus(o) === next) return
      this.ordersActionError = ''
      this.orderStatusUpdatingId = o.id
      try {
        await updateOrderStatus(o.id, next)
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not update status.'
      } finally {
        this.orderStatusUpdatingId = null
      }
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
        await cancelUnpaidOrder(this.cancelOrderTarget.id)
        this.cancelOrderDialogOpen = false
        this.cancelOrderTarget = null
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not cancel order.'
      } finally {
        this.cancellingOrderId = null
      }
    },
    openDeleteOrderDialog(o) {
      if (!o || !o.id) return
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
      const t = this.deleteOrderTarget
      if (!t || !t.id) return
      this.ordersActionError = ''
      this.deletingOrderId = t.id
      try {
        await deleteOrderPermanently(t.id)
        this.deleteOrderDialogOpen = false
        this.deleteOrderTarget = null
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not delete order.'
      } finally {
        this.deletingOrderId = null
      }
    },
    openInvoicePrint(o) {
      if (!o || !o.id) return
      const r = this.$router.resolve({
        name: 'admin-order-invoice',
        params: { orderId: o.id }
      })
      window.open(r.href, '_blank', 'noopener,noreferrer')
    },
    async confirmPayment(o) {
      this.ordersActionError = ''
      this.confirmingId = o.id
      try {
        await confirmOrderPayment(o.id)
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
        const s = await fetchShopSettings()
        this.deliveryFeeDraft = String(Number.isFinite(s.deliveryFeeZar) ? s.deliveryFeeZar : 50)
        this.deliveryFeeModeDraft = s.deliveryFeeMode === 'per_km' ? 'per_km' : 'standard'
        this.deliveryFeePerKmDraft = String(
          Number.isFinite(s.deliveryFeePerKmZar) ? s.deliveryFeePerKmZar : 8
        )
        this.storeLat = Number.isFinite(s.storeLat) ? s.storeLat : null
        this.storeLng = Number.isFinite(s.storeLng) ? s.storeLng : null
        this.storeNameDraft = s.storeName || ''
        this.bankNameDraft = s.bankName || ''
        this.bankAccountHolderDraft = s.bankAccountHolder || ''
        this.bankAccountNumberDraft = s.bankAccountNumber || ''
        this.bankBranchCodeDraft = s.bankBranchCode || ''
        this.eftNotesDraft = s.eftBankInstructions || ''
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
        await updateShopSettings({
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
        await updateStoreBranding({
          storeName: this.storeNameDraft,
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
        await updateBankingDetails({
          bankName: this.bankNameDraft,
          bankAccountHolder: this.bankAccountHolderDraft,
          bankAccountNumber: this.bankAccountNumberDraft,
          bankBranchCode: this.bankBranchCodeDraft,
          eftBankInstructions: this.eftNotesDraft
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
        await updateContactDetails({
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
          category: this.category,
          price: this.price,
          stock: this.initialStock,
          file: this.file
        })
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
        await updateProduct({
          id: this.productEdit.id,
          name: this.editProductName,
          category: this.editProductCategory,
          price: this.editProductPrice,
          stock: this.editProductStock,
          imageFile: this.editProductImageFile || null,
          previousImagePath: this.productEdit.imagePath || null
        })
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
        await deleteProduct({ id: removedId })
        this.deleteError = ''
        this.deleteDialogOpen = false
        this.deleteTarget = null
        // Update list immediately — Realtime refetch can lag or be unavailable; do not rely on it alone.
        this.products = (this.products || []).filter((x) => x.id !== removedId)
      } catch (e) {
        this.deleteError = e && e.message ? e.message : 'Delete failed.'
      } finally {
        this.deletingId = null
      }
    }
  }
}