<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4 help-contact-card" elevation="3" rounded="xl">
      <div class="card-label mb-3">Help contact (shown to merchants)</div>
      <v-row dense>
        <v-col cols="12" sm="6">
          <v-text-field v-model="form.supportEmail" outlined dense hide-details="auto" label="Support email" />
        </v-col>
        <v-col cols="12" sm="6">
          <v-text-field v-model="form.supportPhone" outlined dense hide-details="auto" label="Phone" />
        </v-col>
        <v-col cols="12" sm="6">
          <v-text-field v-model="form.whatsapp" outlined dense hide-details="auto" label="WhatsApp" />
        </v-col>
        <v-col cols="12" sm="6">
          <v-text-field v-model="form.hoursText" outlined dense hide-details="auto" label="Hours" />
        </v-col>
        <v-col cols="12">
          <v-textarea v-model="form.notes" outlined dense hide-details="auto" label="Notes" rows="3" auto-grow />
        </v-col>
      </v-row>
      <v-btn color="primary" class="text-none font-weight-bold mt-3" :loading="saving" @click="save">Save</v-btn>
    </v-card>
  </div>
</template>

<script>
import { fetchSupportHelpContact, updateSupportHelpContact } from '@/services/supportApi'

export default {
  name: 'SupportHelpContactView',
  data() {
    return {
      error: '',
      saving: false,
      form: { supportEmail: '', supportPhone: '', whatsapp: '', hoursText: '', notes: '' }
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      try {
        this.form = await fetchSupportHelpContact()
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load'
      }
    },
    async save() {
      this.saving = true
      this.error = ''
      try {
        this.form = await updateSupportHelpContact(this.form)
      } catch (e) {
        this.error = (e && e.message) || 'Save failed (platform admin)'
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.help-contact-card {
  max-width: 760px;
}
</style>
