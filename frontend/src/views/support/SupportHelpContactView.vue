<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl" style="max-width: 560px">
      <div class="card-label mb-3">Help contact (shown to merchants)</div>
      <v-text-field v-model="form.supportEmail" outlined dense label="Support email" class="mb-3" />
      <v-text-field v-model="form.supportPhone" outlined dense label="Phone" class="mb-3" />
      <v-text-field v-model="form.whatsapp" outlined dense label="WhatsApp" class="mb-3" />
      <v-text-field v-model="form.hoursText" outlined dense label="Hours" class="mb-3" />
      <v-textarea v-model="form.notes" outlined dense label="Notes" rows="3" class="mb-4" />
      <v-btn color="primary" class="text-none font-weight-bold" :loading="saving" @click="save">Save</v-btn>
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
