<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
      <div class="d-flex align-center mb-4">
        <div class="card-label mb-0">Notifications</div>
        <v-spacer />
        <v-btn text class="text-none" :loading="loading" @click="markAll">Mark all read</v-btn>
        <v-btn depressed color="primary" class="text-none ml-2" :loading="loading" @click="load">Refresh</v-btn>
      </div>
      <v-alert v-if="error" type="error" dense outlined class="mb-3 rounded-lg">{{ error }}</v-alert>
      <v-list two-line class="pa-0">
        <v-list-item v-for="n in items" :key="n.id" :class="{ 'grey lighten-4': !n.read }" @click="markOne(n)">
          <v-list-item-avatar>
            <v-icon :color="n.read ? 'grey' : 'primary'">{{ n.read ? 'notifications' : 'notifications_active' }}</v-icon>
          </v-list-item-avatar>
          <v-list-item-content>
            <v-list-item-title>{{ n.title }}</v-list-item-title>
            <v-list-item-subtitle class="text-wrap">{{ n.body }}</v-list-item-subtitle>
          </v-list-item-content>
          <v-list-item-action>
            <span class="text-caption">{{ (n.createdAt || '').slice(0, 16) }}</span>
          </v-list-item-action>
        </v-list-item>
      </v-list>
      <p v-if="!loading && !items.length" class="text-body-2 text--secondary mb-0">No notifications yet.</p>
    </v-card>
  </div>
</template>

<script>
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead
} from '@/services/teamApi'

export default {
  name: 'AdminNotificationsView',
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    return { loading: false, error: '', items: [] }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user
    }
  },
  watch: {
    user: {
      immediate: true,
      handler(u) {
        if (u) this.load()
      }
    }
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const res = await fetchNotifications(this.$route)
        this.items = Array.isArray(res.notifications) ? res.notifications : []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load'
      } finally {
        this.loading = false
      }
    },
    async markOne(n) {
      if (n.read) return
      try {
        await markNotificationRead(this.$route, n.id)
        n.read = true
        this.$root.$emit('admin-notifications-changed')
      } catch {
        /* ignore */
      }
    },
    async markAll() {
      try {
        await markAllNotificationsRead(this.$route)
        await this.load()
        this.$root.$emit('admin-notifications-changed')
      } catch (e) {
        this.error = (e && e.message) || 'Failed'
      }
    }
  }
}
</script>
