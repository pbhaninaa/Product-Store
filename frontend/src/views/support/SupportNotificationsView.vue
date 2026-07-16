<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="d-flex mb-3">
        <div class="card-label mb-0">Platform notifications</div>
        <v-spacer />
        <v-btn text small class="text-none" @click="markAll">Mark all read</v-btn>
        <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
      </div>
      <v-list two-line>
        <v-list-item v-for="n in notifications" :key="n.id" @click="markOne(n)">
          <v-list-item-content>
            <v-list-item-title :class="{ 'font-weight-bold': !n.read }">{{ n.title }}</v-list-item-title>
            <v-list-item-subtitle>{{ n.body }}</v-list-item-subtitle>
          </v-list-item-content>
          <v-list-item-action>
            <span class="text-caption">{{ n.createdAt }}</span>
          </v-list-item-action>
        </v-list-item>
        <v-list-item v-if="!notifications.length">
          <v-list-item-content>
            <v-list-item-title class="text--secondary">No notifications</v-list-item-title>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </v-card>
  </div>
</template>

<script>
import {
  fetchSupportNotifications,
  markAllSupportNotificationsRead,
  markSupportNotificationRead
} from '@/services/supportApi'

export default {
  name: 'SupportNotificationsView',
  data() {
    return { loading: false, error: '', notifications: [] }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const res = await fetchSupportNotifications()
        this.notifications = (res && res.notifications) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load'
      } finally {
        this.loading = false
      }
    },
    async markOne(n) {
      if (n.read) return
      try {
        await markSupportNotificationRead(n.id)
        n.read = true
      } catch {
        // ignore
      }
    },
    async markAll() {
      try {
        await markAllSupportNotificationsRead()
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Failed'
      }
    }
  }
}
</script>
