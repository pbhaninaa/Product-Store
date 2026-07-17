<template>
  <v-dialog :value="value" max-width="560" scrollable @input="$emit('input', $event)">
    <v-card v-if="notification" class="notification-detail rounded-xl">
      <div class="notification-detail__hero" :class="`notification-detail__hero--${presentation.tone}`">
        <v-btn
          icon
          small
          text
          class="notification-detail__close"
          aria-label="Close"
          @click="$emit('input', false)"
        >
          <v-icon>close</v-icon>
        </v-btn>

        <v-avatar class="notification-detail__icon" :color="presentation.color" size="56" tile>
          <v-icon dark>{{ presentation.icon }}</v-icon>
        </v-avatar>

        <h2 class="notification-detail__title">{{ notification.title || presentation.title }}</h2>
        <p class="notification-detail__time">{{ whenLabel }}</p>
      </div>

      <v-card-text class="notification-detail__body">
        <p v-if="bodyText" class="notification-detail__message">{{ bodyText }}</p>

        <v-alert
          v-if="!actionLink"
          type="info"
          dense
          outlined
        >
          This is an update only. No action is required.
        </v-alert>
      </v-card-text>

      <v-card-actions class="notification-detail__actions">
        <v-btn text @click="$emit('input', false)">
          Close
        </v-btn>
        <v-spacer />
        <v-btn
          v-if="actionLink"
          depressed
          color="primary"
          class="text-none font-weight-bold"
          @click="$emit('action', notification)"
        >
          {{ actionLink.label }}
          <v-icon right small>arrow_forward</v-icon>
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
import { getNotificationPresentation } from '@/services/notificationPresentation'

export default {
  name: 'NotificationDetailDialog',
  props: {
    value: { type: Boolean, default: false },
    notification: { type: Object, default: null },
    whenLabel: { type: String, default: '' },
    actionLink: { type: Object, default: null }
  },
  computed: {
    presentation() {
      return getNotificationPresentation(this.notification)
    },
    bodyText() {
      return String(this.notification && this.notification.body ? this.notification.body : '').trim()
    }
  }
}
</script>

<style scoped>
.notification-detail {
  overflow: hidden;
}

.notification-detail__hero {
  position: relative;
  padding: 28px 24px 22px;
  text-align: center;
  background: linear-gradient(135deg, rgba(25, 118, 210, 0.12) 0%, rgba(255, 255, 255, 1) 82%);
}

.notification-detail__hero--success {
  background: linear-gradient(135deg, rgba(46, 125, 50, 0.14) 0%, rgba(255, 255, 255, 1) 82%);
}

.notification-detail__hero--warning,
.notification-detail__hero--action {
  background: linear-gradient(135deg, rgba(251, 140, 0, 0.14) 0%, rgba(255, 255, 255, 1) 82%);
}

.notification-detail__hero--error {
  background: linear-gradient(135deg, rgba(229, 57, 53, 0.14) 0%, rgba(255, 255, 255, 1) 82%);
}

.notification-detail__close {
  position: absolute;
  top: 12px;
  right: 12px;
}

.notification-detail__icon {
  margin: 0 auto 14px;
  border-radius: 14px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
}

.notification-detail__title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: #1f2937;
}

.notification-detail__time {
  margin: 8px 0 0;
  color: rgba(31, 41, 55, 0.65);
  font-size: 0.82rem;
}

.notification-detail__body {
  padding-top: 24px;
}

.notification-detail__message {
  margin: 0 0 16px;
  white-space: pre-wrap;
  line-height: 1.6;
}

.notification-detail__actions {
  padding: 0 24px 20px;
}
</style>
