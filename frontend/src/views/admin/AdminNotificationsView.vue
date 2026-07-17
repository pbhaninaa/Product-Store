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

      <div v-if="items.length" class="notifications-list">
        <NotificationCard
          v-for="n in items"
          :key="n.id"
          :notification="n"
          :when-label="formatWhen(n.createdAt)"
          :action-link="actionFor(n)"
          :unread="!n.read"
          @open="openNotification"
          @action="goToAction"
        />
      </div>

      <p v-else-if="!loading" class="text-body-2 text--secondary mb-0">No notifications yet.</p>
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
import { fetchNotifications, markAllNotificationsRead, markNotificationRead } from '@/services/teamApi'

export default {
  name: 'AdminNotificationsView',
  components: {
    NotificationCard,
    NotificationDetailDialog
  },
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    return { loading: false, error: '', items: [], detailOpen: false, selected: null }
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
    formatWhen(value) {
      if (!value) return ''
      try {
        return new Date(value).toLocaleString()
      } catch {
        return String(value)
      }
    },
    actionFor(notification) {
      return resolveNotificationLink(notification, { route: this.$route })
    },
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
      if (!n || n.read) return
      try {
        await markNotificationRead(this.$route, n.id)
        n.read = true
        this.$root.$emit('admin-notifications-changed')
      } catch {
        /* ignore */
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

<style scoped>
.notifications-list {
  display: grid;
  gap: 14px;
}
</style>
