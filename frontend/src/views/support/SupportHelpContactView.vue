<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4 help-contact-card" elevation="3" rounded="xl">
      <div class="card-label mb-3">Help contact (shown to merchants)</div>
      <v-form ref="contactForm" @submit.prevent>
        <v-row dense>
          <v-col cols="12" sm="6">
            <v-text-field
              v-model="form.supportEmail"
              outlined
              dense
              hide-details="auto"
              label="Support email *"
              type="email"
              :rules="[rules.required('Support email'), rules.email(), rules.maxLength(320, 'Email')]"
            />
          </v-col>
          <v-col cols="12" sm="6">
            <v-text-field
              v-model="form.supportPhone"
              outlined
              dense
              hide-details="auto"
              label="Phone"
              type="tel"
              :rules="[rules.phone(), rules.maxLength(40, 'Phone')]"
            />
          </v-col>
          <v-col cols="12" sm="6">
            <v-text-field
              v-model="form.whatsapp"
              outlined
              dense
              hide-details="auto"
              label="WhatsApp"
              type="tel"
              :rules="[rules.phone(), rules.maxLength(40, 'WhatsApp')]"
            />
          </v-col>
          <v-col cols="12" sm="6">
            <v-text-field
              v-model="form.hoursText"
              outlined
              dense
              hide-details="auto"
              label="Hours"
              :rules="[rules.maxLength(200, 'Hours')]"
            />
          </v-col>
          <v-col cols="12">
            <v-textarea
              v-model="form.notes"
              outlined
              dense
              hide-details="auto"
              label="Notes"
              rows="3"
              auto-grow
              counter="1000"
              :rules="[rules.maxLength(1000, 'Notes')]"
            />
          </v-col>
        </v-row>
      </v-form>
      <v-btn color="primary" class="text-none font-weight-bold mt-3" :loading="saving" @click="save">Save</v-btn>
    </v-card>
  </div>
</template>

<script>
import { fetchSupportHelpContact, updateSupportHelpContact } from '@/services/supportApi'
import { rules, validateForm } from '@/utils/validation'

export default {
  name: 'SupportHelpContactView',
  data() {
    return {
      error: '',
      saving: false,
      form: { supportEmail: '', supportPhone: '', whatsapp: '', hoursText: '', notes: '' },
      rules
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
      if (!validateForm(this.$refs.contactForm)) return
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
