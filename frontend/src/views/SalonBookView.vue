<template>
  <div>
    <section class="hero">
      <v-container class="hero-container py-10 py-md-16 px-3 px-sm-4">
        <v-row align="center">
          <v-col cols="12" md="7" class="pr-md-8">
            <router-link
              :to="servicesLink"
              class="back-link text-body-2 font-weight-bold d-inline-flex align-center mb-4"
            >
              <v-icon small class="mr-1" color="primary">arrow_back</v-icon>
              Back to services
            </router-link>

            <div class="hero-eyebrow mb-3">
              <span class="hero-eyebrow__dot" />
              Book appointment
            </div>
            <h1 class="hero-title">
              Choose your <span class="hero-title__accent">date and time</span>.
            </h1>
            <p class="hero-lead">
              Pick the day you’d like to visit — we’ll show live openings. Tap a time to open the booking window for
              your details and payment. (This page is for <strong>salon appointments</strong> only — product purchases
              use the shop cart and checkout.)
            </p>

            <div class="hero-search-wrap mt-8">
              <v-menu
                v-model="dateMenu"
                :close-on-content-click="false"
                transition="scale-transition"
                offset-y
                min-width="290px"
              >
                <template #activator="{ on, attrs }">
                  <v-text-field
                    :value="dateLabel"
                    solo
                    flat
                    hide-details
                    height="56"
                    readonly
                    class="hero-search hero-search-field hero-date-field"
                    prepend-inner-icon="event"
                    aria-label="Appointment date — opens calendar"
                    :disabled="loadingSlots && !slots.length"
                    v-bind="attrs"
                    v-on="on"
                  />
                </template>
                <v-date-picker
                  :value="date"
                  color="primary"
                  header-color="primary"
                  :first-day-of-week="1"
                  @input="onDatePicked"
                />
              </v-menu>
              <router-link
                v-if="showShopBrowseLink"
                :to="shopHomePath"
                class="hero-shop-link text-body-2 font-weight-medium d-inline-flex align-center mt-3"
              >
                <v-icon small class="mr-1" color="secondary">store</v-icon>
                Browse the product shop (separate from booking)
              </router-link>
              <div class="hero-meta text-caption text--secondary mt-3">
                <v-icon small color="secondary" class="mr-1">bolt</v-icon>
                Times update in place when you pick a new day — no full-page reload
              </div>
            </div>
          </v-col>

          <v-col
            cols="12"
            md="5"
            :class="heroAdvertUrl ? 'd-flex justify-center mt-8 mt-md-0' : 'd-none d-md-flex justify-center'"
          >
            <div v-if="heroAdvertUrl" class="hero-visual hero-visual--advert">
              <img :src="heroAdvertUrl" alt="" class="hero-advert-img" loading="eager" />
            </div>
            <div v-else class="hero-visual" aria-hidden="true">
              <div class="hero-visual__ring" />
              <div class="hero-visual__card hero-visual__card--1" />
              <div class="hero-visual__card hero-visual__card--2" />
              <div class="hero-visual__badge">
                <v-icon color="white" small>local_offer</v-icon>
              </div>
            </div>
          </v-col>
        </v-row>
      </v-container>
    </section>

    <v-container class="pb-12 pb-md-16 px-3 px-sm-4">
      <v-alert v-if="error && !bookingDialogOpen" type="error" border="left" colored-border prominent class="mb-10 rounded-lg">
        {{ error }}
      </v-alert>

      <div ref="slotsAnchor" class="section-head d-flex flex-column flex-sm-row align-start align-sm-end mb-8">
        <div>
          <div class="section-kicker">Availability</div>
          <h2 class="section-title">Available times</h2>
        </div>
        <div v-if="!loadingSlots && slots.length && !slotsRefreshing" class="section-count text-body-2 text--secondary mt-2 mt-sm-0">
          {{ slots.length }} slot{{ slots.length === 1 ? '' : 's' }} on {{ dateLabel }}
        </div>
      </div>

      <v-progress-linear
        v-if="slotsRefreshing"
        height="3"
        indeterminate
        rounded
        color="primary"
        class="mb-4"
        aria-label="Updating availability"
      />

      <v-row v-if="loadingSlots && !slots.length" class="slot-grid">
        <v-col v-for="n in 8" :key="n" cols="12" sm="6" md="4" lg="3">
          <v-skeleton-loader type="image" class="rounded-xl skeleton-slot" height="168" />
        </v-col>
      </v-row>

      <div
        v-else-if="!slots.length && date"
        class="empty-state rounded-xl pa-10 pa-md-14 text-center"
      >
        <div class="empty-state__icon-wrap mb-6">
          <v-icon size="44" color="white">event_busy</v-icon>
        </div>
        <h3 class="empty-state__title mb-2">No available slots</h3>
        <p class="empty-state__text text--secondary mb-0">
          There are no available time slots for {{ dateLabel }}. Please try selecting a different date or contact us directly to check availability.
        </p>
      </div>

      <div
        v-else-if="!slots.length && !date"
        class="empty-state rounded-xl pa-10 pa-md-14 text-center"
      >
        <div class="empty-state__icon-wrap mb-6">
          <v-icon size="44" color="white">event</v-icon>
        </div>
        <h3 class="empty-state__title mb-2">Select a date</h3>
        <p class="empty-state__text text--secondary mb-0">
          Please select a date above to view available booking times.
        </p>
      </div>

      <v-row v-else class="slot-grid">
        <v-col v-for="s in slots" :key="s.startAt" cols="12" sm="6" md="4" lg="3">
          <v-hover v-slot="{ hover }">
            <v-card
              rounded="xl"
              class="slot-card"
              :class="{ 'slot-card--selected': selectedStartAt === s.startAt, 'slot-card--hover': hover }"
              :elevation="selectedStartAt === s.startAt ? 8 : hover ? 12 : 2"
              role="button"
              tabindex="0"
              @click="selectSlot(s)"
              @keydown.enter.prevent="selectSlot(s)"
              @keydown.space.prevent="selectSlot(s)"
            >
              <div class="slot-card__accent" aria-hidden="true" />
              <v-card-text class="slot-card__body pt-6 pb-5 px-5">
                <div class="slot-card__time">{{ formatTime(s.startAt) }}</div>
                <div class="slot-card__hint text-caption text--secondary mt-2">
                  {{ selectedStartAt === s.startAt ? 'Selected — tap again to reopen' : 'Tap to book this time' }}
                </div>
                <v-chip
                  v-if="selectedStartAt === s.startAt"
                  small
                  color="primary"
                  text-color="white"
                  class="mt-4 font-weight-bold"
                >
                  Your time
                </v-chip>
              </v-card-text>
            </v-card>
          </v-hover>
        </v-col>
      </v-row>

      <v-dialog
        v-model="bookingDialogOpen"
        max-width="560"
        scrollable
        :persistent="bookingDialogPersistent"
        content-class="salon-booking-dialog"
      >
        <v-card v-if="selectedStartAt" rounded="lg" class="booking-dialog-card">
          <v-card-title class="text-h6 font-weight-bold pb-1 d-flex align-start">
            <span class="pr-2">Book {{ formatTime(selectedStartAt) }}</span>
            <v-spacer />
            <v-btn icon small aria-label="Close" :disabled="bookingDialogPersistent" @click="closeBookingDialog">
              <v-icon>close</v-icon>
            </v-btn>
          </v-card-title>
          <v-card-subtitle class="pb-2 text-body-2">{{ dateLabel }}</v-card-subtitle>
          <v-divider />
          <v-card-text class="pt-4 pb-2">
            <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg">{{ error }}</v-alert>

            <template v-if="!bookingDialogSuccessPhase">
              <div class="text-subtitle-2 font-weight-bold mb-3">Your details</div>
              <v-text-field
                v-model="customerName"
                solo
                flat
                hide-details="auto"
                height="56"
                label="Full name"
                prepend-inner-icon="person"
                class="details-field rounded-lg mb-3"
              />
              <v-text-field
                v-model="customerPhone"
                solo
                flat
                hide-details="auto"
                height="56"
                label="Phone"
                prepend-inner-icon="phone"
                class="details-field rounded-lg mb-3"
              />
              <v-text-field
                v-model="customerEmail"
                solo
                flat
                hide-details="auto"
                height="56"
                label="Email"
                prepend-inner-icon="email"
                class="details-field rounded-lg mb-2"
              />

              <div v-if="showPaymentSection" class="mt-5">
                <div class="text-subtitle-2 font-weight-bold mb-2">
                  <template v-if="forcedPaymentMethod">Payment</template>
                  <template v-else>Payment method <span class="error--text">*</span></template>
                </div>
                <v-alert
                  v-if="forcedPaymentMethod === 'eft'"
                  type="info"
                  dense
                  outlined
                  class="rounded-lg mb-0"
                >
                  This store only accepts <strong>EFT</strong>. After you submit the booking you must upload proof of
                  payment before the appointment is confirmed.
                </v-alert>
                <v-alert
                  v-else-if="forcedPaymentMethod === 'cash_store'"
                  type="info"
                  dense
                  outlined
                  class="rounded-lg mb-0"
                >
                  This store only accepts <strong>pay in store</strong>. After you submit, you’ll get a
                  <strong>payment code</strong> — pay at the salon and give staff the code so they can mark your booking
                  paid.
                </v-alert>
                <template v-else>
                  <v-radio-group v-model="paymentMethod" hide-details class="mt-0 payment-radio-group">
                    <v-radio v-if="shopAcceptEft" class="payment-radio" value="eft">
                      <template #label>
                        <div>
                          <span class="font-weight-medium">EFT (bank transfer)</span>
                          <div class="text-caption text--secondary mt-1">
                            After booking you must upload proof of payment — your slot stays pending until then.
                          </div>
                        </div>
                      </template>
                    </v-radio>
                    <v-radio v-if="shopAcceptCash" class="payment-radio mt-2" value="cash_store">
                      <template #label>
                        <div>
                          <span class="font-weight-medium">Pay in store</span>
                          <div class="text-caption text--secondary mt-1">
                            You’ll receive a payment code after booking; staff enters it when you pay.
                          </div>
                        </div>
                      </template>
                    </v-radio>
                  </v-radio-group>
                  <v-alert v-if="!paymentMethod" type="info" dense outlined class="mt-3 rounded-lg mb-0">
                    Choose how you will pay — EFT bookings need a proof upload after you submit this form.
                  </v-alert>
                </template>
              </div>
            </template>

            <template v-else>
              <v-alert
                v-if="lastBooking && !lastBooking.needsEftProof"
                type="success"
                border="left"
                colored-border
                prominent
                class="rounded-lg mb-0"
              >
                <span v-if="lastBooking.bookingStatus === 'confirmed'">Booking confirmed (paid).</span>
                <span v-else>Booking recorded — payment <strong>pending</strong>.</span>
                Reference:
                <strong class="d-block mt-1">{{ lastBooking.bookingId }}</strong>
                <template v-if="lastBooking.needsCashPaymentCode && lastBooking.cashPaymentCode">
                  <div class="text-body-2 mt-3 mb-1">Your payment code (give this to staff when you pay):</div>
                  <code class="d-block pa-2 rounded" style="background: rgba(0, 0, 0, 0.06); font-size: 1.1rem">{{
                    lastBooking.cashPaymentCode
                  }}</code>
                </template>
              </v-alert>

              <template v-if="lastBooking && lastBooking.needsEftProof">
                <v-alert type="info" border="left" colored-border prominent class="rounded-lg mb-4">
                  <div class="font-weight-bold mb-1">EFT — upload proof of payment (required)</div>
                  <p class="mb-2 text-body-2">
                    Transfer using your bank app, then upload a screenshot or photo of the proof. Your booking is not
                    complete until we receive this. Use this
                    <strong>payment reference</strong> so we can match your payment automatically:
                  </p>
                  <code class="d-block pa-2 rounded" style="background: rgba(0, 0, 0, 0.06); word-break: break-all">{{
                    lastBooking.paymentReferenceHint
                  }}</code>
                </v-alert>
                <v-text-field
                  v-model="eftBankReference"
                  solo
                  flat
                  hide-details="auto"
                  height="56"
                  label="Reference you used on the transfer"
                  prepend-inner-icon="tag"
                  class="details-field rounded-lg mb-3"
                />
                <div class="mb-2">
                  <div class="text-caption text--secondary mb-2">
                    Proof of payment — PDF (recommended: amount + date checked automatically) or image (reference
                    checked)
                  </div>
                  <v-file-input
                    v-model="eftProofFile"
                    solo
                    flat
                    hide-details="auto"
                    prepend-icon="attach_file"
                    accept="application/pdf,image/jpeg,image/png,image/gif,image/webp,.pdf"
                    label="Choose PDF or image"
                    class="details-field rounded-lg"
                    truncate-length="28"
                  />
                </div>
                <v-btn
                  color="primary"
                  depressed
                  large
                  block
                  class="text-none font-weight-bold btn-amber"
                  :loading="eftProofSubmitting"
                  :disabled="!canSubmitEftProof"
                  @click="submitEftProof"
                >
                  Upload proof
                </v-btn>
              </template>
              <v-alert v-if="eftProofError" type="error" dense outlined class="mt-4 rounded-lg">{{ eftProofError }}</v-alert>
              <v-alert v-if="eftProofSuccessMsg" type="success" dense outlined class="mt-4 rounded-lg">{{
                eftProofSuccessMsg
              }}</v-alert>
            </template>
          </v-card-text>
          <v-divider />
          <v-card-actions class="pa-4 flex-wrap">
            <v-btn text class="text-none" :disabled="bookingDialogPersistent" @click="closeBookingDialog">
              {{ bookingDialogShowDone ? 'Close' : 'Cancel' }}
            </v-btn>
            <v-spacer />
            <v-btn
              v-if="bookingDialogShowDone"
              color="primary"
              depressed
              class="text-none font-weight-bold btn-amber"
              @click="closeBookingDialog"
            >
              Done
            </v-btn>
            <v-btn
              v-else-if="!bookingDialogSuccessPhase"
              color="primary"
              depressed
              large
              class="text-none font-weight-bold btn-amber"
              :loading="submitting"
              :disabled="!canSubmit"
              @click="submit"
            >
              {{ submitBookingButtonLabel }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>
    </v-container>
  </div>
</template>

<script>
import { fetchShopSettings } from '@/services/publicStore'
import { isSalonAndStoreShopType, isSalonShopType } from '@/services/shopType'
import { createSalonBooking, fetchSalonAvailability, submitSalonBookingEftProof } from '@/services/salonPublic'

export default {
  name: 'SalonBookView',
  inject: {
    shopDisplay: {
      default: () => ({ storeName: '', logoUrl: '', heroUrl: '', shopType: 'normal_store' })
    }
  },
  data() {
    const today = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const yyyyMmDd = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`
    return {
      date: yyyyMmDd,
      dateMenu: false,
      slots: [],
      loadingSlots: true,
      slotsLoadedOnce: false,
      slotsRefreshing: false,
      selectedStartAt: '',
      customerName: '',
      customerPhone: '',
      customerEmail: '',
      submitting: false,
      error: '',
      shopAcceptEft: true,
      shopAcceptCash: true,
      /** Empty until customer picks when both EFT and cash are enabled. */
      paymentMethod: '',
      lastBooking: null,
      eftBankReference: '',
      eftProofFile: null,
      eftProofSubmitting: false,
      eftProofError: '',
      eftProofSuccessMsg: '',
      bookingDialogOpen: false
    }
  },
  computed: {
    merchantSlug() {
      return String(this.$route.params.merchantSlug || '').trim()
    },
    serviceId() {
      return String(this.$route.params.serviceId || '').trim()
    },
    servicesLink() {
      return `/m/${encodeURIComponent(this.merchantSlug)}/salon/services`
    },
    heroAdvertUrl() {
      const u = this.shopDisplay && String(this.shopDisplay.heroUrl || '').trim()
      return u || ''
    },
    shopHomePath() {
      return { name: 'merchant-home', params: { merchantSlug: this.merchantSlug || 'demo' } }
    },
    showShopBrowseLink() {
      const st = this.shopDisplay && this.shopDisplay.shopType
      return isSalonAndStoreShopType(st)
    },
    dateLabel() {
      try {
        const [y, m, d] = String(this.date || '').split('-').map(Number)
        if (!y || !m || !d) return this.date
        const dt = new Date(y, m - 1, d)
        return dt.toLocaleDateString(undefined, {
          weekday: 'short',
          month: 'short',
          day: 'numeric',
          year: 'numeric'
        })
      } catch {
        return this.date
      }
    },
    showPaymentSection() {
      return this.shopAcceptEft || this.shopAcceptCash
    },
    canSubmit() {
      const pmOk =
        (this.shopAcceptEft || this.shopAcceptCash) &&
        (this.paymentMethod === 'eft' || this.paymentMethod === 'cash_store')
      return (
        !this.submitting &&
        this.selectedStartAt &&
        String(this.customerName).trim().length >= 2 &&
        String(this.customerPhone).trim().length >= 7 &&
        String(this.customerEmail).trim().length >= 5 &&
        pmOk
      )
    },
    submitBookingButtonLabel() {
      if (this.paymentMethod === 'cash_store') return 'Confirm booking (pay in store)'
      return 'Request booking (EFT)'
    },
    eftProofFileSingle() {
      const f = this.eftProofFile
      if (f instanceof File) return f
      if (Array.isArray(f) && f[0] instanceof File) return f[0]
      return null
    },
    isValidEftProofFile() {
      const f = this.eftProofFileSingle
      if (!f) return false
      const t = String(f.type || '').toLowerCase()
      const n = String(f.name || '').toLowerCase()
      if (t === 'application/pdf' || n.endsWith('.pdf')) return true
      if (t.startsWith('image/') || /\.(jpe?g|png|gif|webp)$/i.test(n)) return true
      return false
    },
    canSubmitEftProof() {
      return (
        !this.eftProofSubmitting &&
        this.lastBooking &&
        this.lastBooking.needsEftProof &&
        String(this.eftBankReference || '').trim().length >= 3 &&
        this.eftProofFileSingle != null &&
        this.isValidEftProofFile
      )
    },
    bookingDialogSuccessPhase() {
      return Boolean(this.lastBooking)
    },
    bookingDialogShowDone() {
      if (!this.lastBooking) return false
      if (!this.lastBooking.needsEftProof) return true
      return Boolean(this.eftProofSuccessMsg)
    },
    bookingDialogPersistent() {
      return Boolean(this.submitting || this.eftProofSubmitting)
    },
    forcedPaymentMethod() {
      if (this.shopAcceptEft && !this.shopAcceptCash) return 'eft'
      if (!this.shopAcceptEft && this.shopAcceptCash) return 'cash_store'
      return ''
    }
  },
  watch: {
    bookingDialogOpen(open, prev) {
      if (!open && prev) this.clearBookingDialogState()
    },
    '$route.params.serviceId'() {
      this.slotsLoadedOnce = false
      this.hydrateDateFromQuery()
      this.loadSlots().then(() => this.maybeScrollToSlots())
    },
    '$route.query.date'() {
      this.hydrateDateFromQuery()
      this.loadSlots().then(() => this.maybeScrollToSlots())
    }
  },
  async created() {
    this.hydrateDateFromQuery()
    const slug = this.merchantSlug
    if (slug) {
      try {
        const s = await fetchShopSettings(slug)
        if (!isSalonShopType(s.shopType)) {
          await this.$router.replace({ name: 'merchant-home', params: { merchantSlug: slug } })
          return
        }
        this.shopAcceptEft = s.acceptCustomerEft !== false
        this.shopAcceptCash = s.acceptCustomerCash !== false
        this.applyDefaultPaymentMethod()
      } catch {
        // continue
      }
    }
    await this.loadSlots()
    this.maybeScrollToSlots()
  },
  methods: {
    hydrateDateFromQuery() {
      const raw = this.$route.query && this.$route.query.date
      const s = raw == null ? '' : String(raw).trim()
      if (/^\d{4}-\d{2}-\d{2}$/.test(s)) {
        this.date = s
      }
    },
    maybeScrollToSlots() {
      const hasDateQuery = this.$route.query && this.$route.query.date
      if (!hasDateQuery) return
      this.$nextTick(() => {
        const ref = this.$refs.slotsAnchor
        const el = ref && (ref.$el || ref)
        if (el && typeof el.scrollIntoView === 'function') {
          el.scrollIntoView({ behavior: 'smooth', block: 'start' })
        }
      })
    },
    scrollToSlots() {
      this.$nextTick(() => {
        const ref = this.$refs.slotsAnchor
        const el = ref && (ref.$el || ref)
        if (el && typeof el.scrollIntoView === 'function') {
          el.scrollIntoView({ behavior: 'smooth', block: 'start' })
        }
      })
    },
    onDatePicked(newDate) {
      this.date = newDate
      this.dateMenu = false
      this.loadSlots()
      this.scrollToSlots()
    },
    onBookingDateChange() {
      this.loadSlots()
    },
    async loadSlots() {
      const hadSlots = this.slots.length > 0
      if (hadSlots) {
        this.slotsRefreshing = true
      } else {
        this.loadingSlots = true
      }
      this.bookingDialogOpen = false
      this.error = ''
      this.lastBooking = null
      this.eftBankReference = ''
      this.eftProofFile = null
      this.eftProofError = ''
      this.eftProofSuccessMsg = ''
      this.selectedStartAt = ''
      try {
        this.slots = await fetchSalonAvailability(this.merchantSlug, this.serviceId, this.date)
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not load slots.'
        this.slots = []
      } finally {
        this.loadingSlots = false
        this.slotsRefreshing = false
      }
      this.applyDefaultPaymentMethod()
    },
    applyDefaultPaymentMethod() {
      if (this.shopAcceptEft && !this.shopAcceptCash) this.paymentMethod = 'eft'
      else if (!this.shopAcceptEft && this.shopAcceptCash) this.paymentMethod = 'cash_store'
      else if (this.shopAcceptEft && this.shopAcceptCash) this.paymentMethod = ''
      else this.paymentMethod = ''
    },
    selectSlot(slot) {
      const prev = this.selectedStartAt
      const next = slot.startAt
      const changed = prev !== next
      this.selectedStartAt = next
      if (changed) {
        this.lastBooking = null
        this.eftBankReference = ''
        this.eftProofFile = null
        this.eftProofError = ''
        this.eftProofSuccessMsg = ''
        this.error = ''
        this.customerName = ''
        this.customerPhone = ''
        this.customerEmail = ''
        this.applyDefaultPaymentMethod()
      }
      this.bookingDialogOpen = true
    },
    clearBookingDialogState() {
      this.selectedStartAt = ''
      this.lastBooking = null
      this.eftBankReference = ''
      this.eftProofFile = null
      this.eftProofError = ''
      this.eftProofSuccessMsg = ''
      this.error = ''
      this.customerName = ''
      this.customerPhone = ''
      this.customerEmail = ''
      this.applyDefaultPaymentMethod()
    },
    closeBookingDialog() {
      this.bookingDialogOpen = false
    },
    formatTime(iso) {
      try {
        const d = new Date(iso)
        return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
      } catch {
        return String(iso)
      }
    },
    async submit() {
      if (!this.canSubmit) return
      this.submitting = true
      this.error = ''
      this.lastBooking = null
      this.eftProofError = ''
      this.eftProofSuccessMsg = ''
      try {
        const created = await createSalonBooking(this.merchantSlug, {
          serviceId: this.serviceId,
          startAt: this.selectedStartAt,
          customerName: this.customerName,
          customerPhone: this.customerPhone,
          customerEmail: this.customerEmail,
          paymentMethod: this.paymentMethod
        })
        this.lastBooking = {
          bookingId: created.bookingId,
          needsEftProof: created.needsEftProof,
          paymentReferenceHint: created.paymentReferenceHint,
          bookingStatus: created.bookingStatus,
          cashPaymentCode: created.cashPaymentCode || '',
          needsCashPaymentCode: Boolean(created.needsCashPaymentCode)
        }
        if (created.needsEftProof) {
          this.eftBankReference = created.paymentReferenceHint || created.bookingId
        }
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not create booking.'
      } finally {
        this.submitting = false
      }
    },
    async submitEftProof() {
      if (!this.canSubmitEftProof) return
      this.eftProofSubmitting = true
      this.eftProofError = ''
      this.eftProofSuccessMsg = ''
      try {
        const res = await submitSalonBookingEftProof(this.merchantSlug, this.lastBooking.bookingId, {
          customerEmail: this.customerEmail,
          bankReference: this.eftBankReference,
          file: this.eftProofFileSingle
        })
        const auto = res && res.autoVerified
        const st = res && res.bookingStatus
        const pv = String((res && res.paymentVerificationState) || '').toLowerCase()
        const manual = Boolean(res && res.manualVerificationRequired) || pv === 'manual_pending'
        const mode = String((res && res.autoVerifyMode) || '')
        if (auto) {
          this.eftProofSuccessMsg =
            mode === 'pdf_amount_and_date'
              ? 'Your bank PDF matched the service price and a recent transaction date — your booking is confirmed.'
              : 'Payment reference matched — your booking is confirmed.'
        } else if (manual || String(st || '').toLowerCase() === 'pending') {
          this.eftProofSuccessMsg =
            mode === 'pdf_amount_and_date'
              ? 'Proof received. The amount or date on your PDF did not match our automatic checks — the merchant will verify it manually.'
              : 'Proof received. We could not auto-verify your reference — the merchant will review it shortly.'
        } else {
          this.eftProofSuccessMsg = 'Proof submitted.'
        }
        if (this.lastBooking) {
          this.lastBooking = {
            ...this.lastBooking,
            needsEftProof: false,
            bookingStatus: String((res && res.bookingStatus) || this.lastBooking.bookingStatus || '')
          }
        }
      } catch (e) {
        this.eftProofError = e && e.message ? e.message : 'Upload failed.'
      } finally {
        this.eftProofSubmitting = false
      }
    }
  }
}
</script>

<style scoped>
.hero {
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 96px;
  background: linear-gradient(180deg, transparent, #eef2f7);
  pointer-events: none;
}

.back-link {
  color: #c2410c !important;
  text-decoration: none !important;
}

.back-link:hover {
  text-decoration: underline !important;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: rgba(15, 23, 42, 0.55);
}

.hero-eyebrow__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ea580c, #c2410c);
  box-shadow: 0 0 0 4px rgba(234, 88, 12, 0.2);
}

.hero-title {
  font-size: clamp(2rem, 4vw, 2.75rem);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.035em;
  color: #0f172a;
  max-width: 18ch;
}

.hero-title__accent {
  background: linear-gradient(120deg, #ea580c, #c2410c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-lead {
  margin-top: 1rem;
  font-size: 1.0625rem;
  line-height: 1.65;
  color: rgba(15, 23, 42, 0.62);
  max-width: 42ch;
}

.hero-search-wrap {
  width: 100%;
  max-width: 440px;
}

.hero-date-field >>> .v-input__slot {
  cursor: pointer;
}

.hero-search-field >>> .v-input__slot {
  border-radius: 16px !important;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.06),
    0 12px 40px -16px rgba(15, 23, 42, 0.25) !important;
  border: 1px solid rgba(15, 23, 42, 0.06) !important;
  padding-left: 4px !important;
  min-height: 56px !important;
}

.hero-search-field >>> input::placeholder {
  color: rgba(100, 116, 139, 0.85);
}

.hero-shop-link {
  color: rgba(30, 58, 95, 0.9) !important;
  text-decoration: none !important;
}

.hero-shop-link:hover {
  color: #c2410c !important;
  text-decoration: underline !important;
}

.hero-meta {
  display: flex;
  align-items: center;
}

.hero-visual {
  position: relative;
  width: 280px;
  height: 280px;
}

.hero-visual--advert {
  width: min(100%, 380px);
  height: auto;
  min-height: 200px;
}

.hero-advert-img {
  width: 100%;
  max-height: 320px;
  object-fit: cover;
  border-radius: 20px;
  box-shadow: 0 24px 50px -20px rgba(15, 23, 42, 0.35);
}

.hero-visual__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1px dashed rgba(15, 23, 42, 0.12);
  animation: spin-slow 28s linear infinite;
}

.hero-visual__card {
  position: absolute;
  border-radius: 20px;
  box-shadow: 0 24px 50px -20px rgba(15, 23, 42, 0.35);
}

.hero-visual__card--1 {
  width: 140px;
  height: 180px;
  left: 50%;
  top: 50%;
  transform: translate(-62%, -58%) rotate(-8deg);
  background: linear-gradient(160deg, #1e3a5f, #0f172a);
}

.hero-visual__card--2 {
  width: 120px;
  height: 150px;
  left: 50%;
  top: 50%;
  transform: translate(-22%, -32%) rotate(10deg);
  background: linear-gradient(160deg, #fed7aa, #fb923c);
}

.hero-visual__badge {
  position: absolute;
  right: 12%;
  top: 18%;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: linear-gradient(135deg, #ea580c, #c2410c);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 28px -10px rgba(194, 65, 12, 0.7);
}

@keyframes spin-slow {
  to {
    transform: rotate(360deg);
  }
}

.section-kicker {
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
  margin-bottom: 0.35rem;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.section-count {
  margin-left: auto;
}

.slot-grid {
  margin-top: -8px;
}

.skeleton-slot >>> .v-skeleton-loader__bone {
  border-radius: 20px;
}

.empty-state {
  border: 1px dashed rgba(15, 23, 42, 0.12);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.75), rgba(248, 250, 252, 0.95));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

.empty-state__icon-wrap {
  width: 88px;
  height: 88px;
  margin-left: auto;
  margin-right: auto;
  border-radius: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #0f172a 0%, #1e3a5f 55%, #c2410c 100%);
  box-shadow: 0 20px 40px -24px rgba(15, 23, 42, 0.5);
}

.empty-state__title {
  font-size: 1.25rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.empty-state__text {
  max-width: 360px;
  margin-left: auto;
  margin-right: auto;
  line-height: 1.6;
}

.slot-card {
  position: relative;
  cursor: pointer;
  border: 2px solid transparent;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
  overflow: hidden;
}

.slot-card--hover:not(.slot-card--selected) {
  transform: translateY(-2px);
}

.slot-card--selected {
  border-color: #c2410c;
}

.slot-card__accent {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 4px;
  background: linear-gradient(90deg, #ea580c, #c2410c);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.slot-card--selected .slot-card__accent {
  opacity: 1;
}

.slot-card__time {
  font-size: 1.75rem;
  font-weight: 700;
  letter-spacing: -0.04em;
  color: #0f172a;
  line-height: 1.1;
}

.details-card {
  border: 1px solid rgba(15, 23, 42, 0.06) !important;
  background: rgba(255, 255, 255, 0.92);
}

.details-field >>> .v-input__slot {
  border-radius: 16px !important;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.06),
    0 8px 28px -12px rgba(15, 23, 42, 0.18) !important;
  border: 1px solid rgba(15, 23, 42, 0.06) !important;
}

.btn-amber {
  background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%) !important;
  box-shadow: 0 8px 24px -8px rgba(194, 65, 12, 0.55) !important;
  max-width: 100%;
}

.payment-radio-group >>> .v-radio {
  align-items: flex-start;
}
.payment-radio >>> .v-input--selection-controls__input {
  margin-top: 2px;
}
</style>
