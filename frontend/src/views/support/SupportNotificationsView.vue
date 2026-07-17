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

      <div v-if="notifications.length" class="notifications-list">
        <NotificationCard
          v-for="n in notifications"
          :key="n.id"
          :notification="n"
          :when-label="formatWhen(n.createdAt)"
          :action-link="actionFor(n)"
          :unread="!n.read"
          @open="openNotification"
          @action="goToAction"
        />
      </div>

      <p v-else-if="!loading" class="text-body-2 text--secondary mb-0">No notifications</p>
    </v-card>

    <NotificationDetailDialog
      :value="detailOpen"
      :notification="selected"
      :when-label="formatWhen(selected && selected.createdAt)"
      :action-link="actionFor(selected)"
      @input="onDialogInput"
      @action="goToAction"
    />
  </div>
</template>

<script>
import NotificationCard from '@/components/NotificationCard.vue'
import NotificationDetailDialog from '@/components/NotificationDetailDialog.vue'
import { resolveNotificationLink } from '@/services/notificationNavigation'
import { fetchSupportNotifications, markAllSupportNotificationsRead, markSupportNotificationRead } from '@/services/supportApi'

export default {
  name: 'SupportNotificationsView',
  components: {
    NotificationCard,
    NotificationDetailDialog
  },
  data() {
    return { loading: false, error: '', notifications: [], detailOpen: false, selected: null }
  },
  created() {
    this.load()
  },
  methods: {
    formatWhen(value) {
      if (!value) return ''
      try {
        return new Date(value).toLocaleString()
      } catch {
        return String(value)
      }
    },
    actionFor(notification) {
      return resolveNotificationLink(notification, { isSupport: true, route: this.$route })
    },
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
      if (!n || n.read) return
      try {
        await markSupportNotificationRead(n.id)
        n.read = true
      } catch {
        // ignore
      }
    },
    async openNotification(notification) {
      this.selected = notification
      this.detailOpen = true
      await this.markOne(notification)
    },
    async goToAction(notification) {
      const action = this.actionFor(notification)
      if (!action || !action.to) return
      await this.markOne(notification)
      this.detailOpen = false
      this.selected = null
      this.$router.push(action.to).catch(() => {})
    },
    onDialogInput(value) {
      this.detailOpen = value
      if (!value) this.selected = null
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

<style scoped>
.notifications-list {
  display: grid;
  gap: 14px;
}
</style>
