<template>
  <article
    class="notification-card"
    :class="[
      `notification-card--${presentation.tone}`,
      { 'notification-card--unread': unread }
    ]"
    role="button"
    tabindex="0"
    @click="$emit('open', notification)"
    @keydown.enter="$emit('open', notification)"
    @keydown.space.prevent="$emit('open', notification)"
  >
    <div class="notification-card__accent" aria-hidden="true" />

    <v-avatar class="notification-card__icon" :color="presentation.color" size="44" tile>
      <v-icon dark>{{ presentation.icon }}</v-icon>
    </v-avatar>

    <div class="notification-card__content">
      <div class="notification-card__top">
        <div class="notification-card__title-row">
          <h3 class="notification-card__title">{{ notification.title || presentation.title }}</h3>
          <span v-if="unread" class="notification-card__dot" aria-label="Unread" />
        </div>
        <time class="notification-card__time">{{ whenLabel }}</time>
      </div>

      <div class="notification-card__meta">
        <v-chip x-small :color="presentation.color" outlined class="notification-card__category">
          {{ presentation.category }}
        </v-chip>
        <v-chip v-if="unread" x-small color="primary" label>
          New
        </v-chip>
      </div>

      <p v-if="bodyPreview" class="notification-card__body">
        {{ bodyPreview }}
      </p>

      <div v-if="actionLink" class="notification-card__footer" @click.stop>
        <v-btn
          small
          depressed
          color="primary"
          class="text-none font-weight-bold notification-card__action"
          @click.stop="$emit('action', notification)"
        >
          {{ actionLink.label }}
          <v-icon right small>arrow_forward</v-icon>
        </v-btn>
      </div>
    </div>
  </article>
</template>

<script>
import { getNotificationPresentation, truncateNotificationText } from '@/services/notificationPresentation'

export default {
  name: 'NotificationCard',
  props: {
    notification: { type: Object, required: true },
    whenLabel: { type: String, default: '' },
    actionLink: { type: Object, default: null },
    unread: { type: Boolean, default: false },
    previewMaxLength: { type: Number, default: 140 }
  },
  computed: {
    presentation() {
      return getNotificationPresentation(this.notification)
    },
    bodyPreview() {
      return truncateNotificationText(this.notification && this.notification.body, this.previewMaxLength)
    }
  }
}
</script>

<style scoped>
.notification-card {
  position: relative;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 14px;
  align-items: start;
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid rgba(26, 32, 44, 0.08);
  background: rgba(255, 255, 255, 0.86);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.15s ease;
}

.notification-card:hover {
  border-color: rgba(25, 118, 210, 0.28);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.notification-card:focus-visible {
  outline: 2px solid #1976d2;
  outline-offset: 2px;
}

.notification-card--unread {
  background: linear-gradient(135deg, rgba(25, 118, 210, 0.06) 0%, rgba(255, 255, 255, 0.95) 55%);
  border-color: rgba(25, 118, 210, 0.18);
}

.notification-card__accent {
  position: absolute;
  left: 0;
  top: 14px;
  bottom: 14px;
  width: 4px;
  border-radius: 0 4px 4px 0;
  background: transparent;
}

.notification-card--unread .notification-card__accent {
  background: #1976d2;
}

.notification-card__icon {
  border-radius: 12px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
  flex-shrink: 0;
}

.notification-card__content {
  min-width: 0;
}

.notification-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.notification-card__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.notification-card__title {
  margin: 0;
  font-size: 0.98rem;
  font-weight: 700;
  line-height: 1.35;
  color: #1f2937;
}

.notification-card__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1976d2;
  flex-shrink: 0;
}

.notification-card__time {
  font-size: 0.75rem;
  color: rgba(31, 41, 55, 0.58);
  white-space: nowrap;
  flex-shrink: 0;
}

.notification-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.notification-card__category {
  font-weight: 600;
}

.notification-card__body {
  margin: 10px 0 0;
  font-size: 0.875rem;
  line-height: 1.5;
  color: rgba(31, 41, 55, 0.78);
  white-space: pre-line;
}

.notification-card__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

@media (max-width: 599px) {
  .notification-card {
    padding: 16px 14px 16px 18px;
  }

  .notification-card__top {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
