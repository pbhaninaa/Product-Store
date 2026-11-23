<template>
  <v-btn
    v-if="visible"
    icon
    small
    :aria-label="enabled ? 'Mute notification sound' : 'Turn on notification sound'"
    :title="enabled ? 'Mute notification sound' : 'Turn on notification sound'"
    @click="toggle"
  >
    <v-icon small>{{ enabled ? 'volume_up' : 'volume_off' }}</v-icon>
  </v-btn>
</template>

<script>
import {
  isInNativeShell,
  isNotificationSoundEnabled,
  setNotificationSoundEnabled
} from '@/services/notificationSound'

/** Mute/unmute the incoming-notification tone. Hidden in the phone app, where the OS plays the sound. */
export default {
  name: 'NotificationSoundToggle',
  data() {
    return { enabled: isNotificationSoundEnabled(), visible: !isInNativeShell() }
  },
  methods: {
    toggle() {
      this.enabled = !this.enabled
      setNotificationSoundEnabled(this.enabled)
    }
  }
}
</script>
