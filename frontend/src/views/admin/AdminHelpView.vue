<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-row>
      <v-col cols="12" md="5">
        <v-card class="admin-card pa-4" elevation="3" rounded="xl">
          <div class="card-label mb-3">Platform contact</div>
          <p class="text-body-2 mb-1"><strong>Email:</strong> {{ contact.supportEmail || '—' }}</p>
          <p class="text-body-2 mb-1"><strong>Phone:</strong> {{ contact.supportPhone || '—' }}</p>
          <p class="text-body-2 mb-1"><strong>WhatsApp:</strong> {{ contact.whatsapp || '—' }}</p>
          <p class="text-body-2 mb-1"><strong>Hours:</strong> {{ contact.hoursText || '—' }}</p>
          <p v-if="contact.notes" class="text-caption text--secondary mt-3 mb-0">{{ contact.notes }}</p>
        </v-card>
      </v-col>
      <v-col cols="12" md="7">
        <v-card class="admin-card pa-4" elevation="3" rounded="xl">
          <div class="card-label mb-3">Open a ticket</div>
          <v-text-field v-model="subject" outlined dense label="Subject" class="mb-3" />
          <v-textarea v-model="body" outlined dense label="Message" rows="4" class="mb-3" />
          <v-btn color="primary" class="text-none font-weight-bold" :loading="sending" @click="send">
            Send ticket
          </v-btn>
        </v-card>
        <v-card class="admin-card pa-4 mt-4" elevation="3" rounded="xl">
          <div class="card-label mb-3">Your tickets</div>
          <v-simple-table dense>
            <thead>
              <tr>
                <th>Subject</th>
                <th>Status</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in tickets" :key="t.id">
                <td>{{ t.subject }}</td>
                <td>{{ t.status }}</td>
                <td>{{ t.createdAt }}</td>
              </tr>
              <tr v-if="!tickets.length">
                <td colspan="3" class="text--secondary">No tickets yet.</td>
              </tr>
            </tbody>
          </v-simple-table>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script>
import { apiFetch } from '@/services/api'
import { requireMerchantSlugForApi } from '@/services/auth'

export default {
  name: 'AdminHelpView',
  data() {
    return {
      error: '',
      contact: {},
      tickets: [],
      subject: '',
      body: '',
      sending: false
    }
  },
  created() {
    this.load()
  },
  methods: {
    base() {
      const slug = requireMerchantSlugForApi(this.$route)
      return `/api/m/${encodeURIComponent(slug)}/admin/help`
    },
    async load() {
      this.error = ''
      try {
        const [c, t] = await Promise.all([
          apiFetch(`${this.base()}/contact`, { auth: true }),
          apiFetch(`${this.base()}/tickets`, { auth: true })
        ])
        this.contact = c || {}
        this.tickets = (t && t.tickets) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load help'
      }
    },
    async send() {
      this.sending = true
      this.error = ''
      try {
        await apiFetch(`${this.base()}/tickets`, {
          method: 'POST',
          auth: true,
          json: { subject: this.subject, body: this.body }
        })
        this.subject = ''
        this.body = ''
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Could not send ticket'
      } finally {
        this.sending = false
      }
    }
  }
}
</script>
