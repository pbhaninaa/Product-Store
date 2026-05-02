<template>
  <div class="contact-page">
    <section class="contact-hero">
      <v-container class="py-10 py-md-14 px-3 px-sm-4">
        <div class="contact-hero__kicker">Get in touch</div>
        <h1 class="contact-hero__title">Contact us</h1>
        <p class="contact-hero__lead text-body-1 text--secondary mb-0">
          Reach the team at <strong>{{ displayStoreName }}</strong> using the details below.
        </p>
      </v-container>
    </section>

    <v-container class="pb-12 pb-md-16 px-3 px-sm-4">
      <v-alert v-if="supabaseConfigHint" type="warning" prominent border="left" colored-border class="mb-8 rounded-lg">
        {{ supabaseConfigHint }}
      </v-alert>

      <v-progress-circular v-else-if="loading" indeterminate color="primary" class="d-block mx-auto my-12" />

      <v-card v-else class="contact-card rounded-xl pa-6 pa-md-10" elevation="2" outlined>
        <template v-if="hasAnyContent">
          <dl class="contact-dl mb-0">
            <template v-if="contactEmail">
              <div class="contact-row">
                <dt><v-icon small color="primary" class="mr-2">email</v-icon> Email</dt>
                <dd>
                  <a :href="'mailto:' + encodeURIComponent(contactEmail)" class="contact-link">{{ contactEmail }}</a>
                </dd>
              </div>
            </template>
            <template v-if="contactPhone">
              <div class="contact-row">
                <dt><v-icon small color="primary" class="mr-2">phone</v-icon> Phone</dt>
                <dd>
                  <a :href="telHref(contactPhone)" class="contact-link">{{ contactPhone }}</a>
                </dd>
              </div>
            </template>
            <template v-if="contactAddress">
              <div class="contact-row">
                <dt><v-icon small color="primary" class="mr-2">place</v-icon> Address</dt>
                <dd class="contact-address">{{ contactAddress }}</dd>
              </div>
            </template>
            <template v-if="contactNotes">
              <div class="contact-row contact-row--notes">
                <dt><v-icon small color="primary" class="mr-2">notes</v-icon> More</dt>
                <dd class="contact-notes">{{ contactNotes }}</dd>
              </div>
            </template>
            <template v-if="hasWorkingHours">
              <div class="contact-row">
                <dt><v-icon small color="primary" class="mr-2">schedule</v-icon> Working hours</dt>
                <dd class="contact-hours">
                  <div
                    v-for="row in workingHoursRows"
                    :key="row.dayOfWeek"
                    class="contact-hours__line d-flex justify-space-between align-baseline"
                  >
                    <span class="contact-hours__day">{{ row.label }}</span>
                    <span class="contact-hours__time text-right">{{ row.line }}</span>
                  </div>
                </dd>
              </div>
            </template>
          </dl>
        </template>
        <div v-else class="text-center py-8">
          <v-icon size="48" color="secondary" class="mb-4">contact_support</v-icon>
          <p class="text-body-1 text--secondary mb-0">
            Contact details haven’t been published yet. Please check back soon or visit the shop.
          </p>
        </div>
      </v-card>
    </v-container>
  </div>
</template>

<script>
import { fetchShopSettings } from '@/services/publicStore'
import { openingHoursRowsForPublicDisplay } from '@/utils/openingHoursDisplay'

export default {
  name: 'ContactView',
  data() {
    return {
      loading: true,
      storeName: '',
      contactEmail: '',
      contactPhone: '',
      contactAddress: '',
      contactNotes: '',
      openingHoursJson: '[]',
      supabaseConfigHint: ''
    }
  },
  computed: {
    displayStoreName() {
      const n = String(this.storeName || '').trim()
      return n.length >= 2 ? n : process.env.VUE_APP_SITE_NAME || 'our store'
    },
    hasAnyContact() {
      return Boolean(
        String(this.contactEmail || '').trim() ||
          String(this.contactPhone || '').trim() ||
          String(this.contactAddress || '').trim() ||
          String(this.contactNotes || '').trim()
      )
    },
    workingHoursRows() {
      return openingHoursRowsForPublicDisplay(this.openingHoursJson)
    },
    hasWorkingHours() {
      return this.workingHoursRows.length > 0
    },
    hasAnyContent() {
      return this.hasAnyContact || this.hasWorkingHours
    }
  },
  async created() {
    const slug = String(this.$route.params.merchantSlug || '').trim()
    try {
      const s = await fetchShopSettings(slug)
      this.storeName = s.storeName || ''
      this.contactEmail = s.contactEmail || ''
      this.contactPhone = s.contactPhone || ''
      this.contactAddress = s.contactAddress || ''
      this.contactNotes = s.contactNotes || ''
      this.openingHoursJson = s.openingHoursJson || '[]'
    } catch {
      // keep empty
    } finally {
      this.loading = false
    }
  },
  methods: {
    telHref(phone) {
      const digits = String(phone || '').replace(/[^\d+]/g, '')
      return digits ? `tel:${digits}` : '#'
    }
  }
}
</script>

<style scoped>
.contact-hero {
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.95) 0%, transparent 100%);
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.contact-hero__kicker {
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
  margin-bottom: 0.5rem;
}

.contact-hero__title {
  font-size: clamp(1.75rem, 4vw, 2.25rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
  margin-bottom: 0.75rem;
}

.contact-card {
  max-width: 640px;
  margin: 0 auto;
  border-color: rgba(15, 23, 42, 0.08) !important;
}

.contact-dl .contact-row {
  padding: 1rem 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.contact-dl .contact-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.contact-row dt {
  display: flex;
  align-items: center;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: rgba(15, 23, 42, 0.5);
  margin-bottom: 0.35rem;
}

.contact-row dd {
  margin: 0;
  font-size: 1.0625rem;
  color: #0f172a;
}

.contact-link {
  color: #c2410c !important;
  font-weight: 600;
  text-decoration: none;
}

.contact-link:hover {
  text-decoration: underline;
}

.contact-address,
.contact-notes {
  white-space: pre-wrap;
  line-height: 1.6;
}

.contact-hours__line {
  gap: 1rem;
  padding: 0.35rem 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.05);
  font-size: 0.9375rem;
}

.contact-hours__line:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.contact-hours__day {
  color: rgba(15, 23, 42, 0.72);
  font-weight: 600;
}

.contact-hours__time {
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}
</style>
