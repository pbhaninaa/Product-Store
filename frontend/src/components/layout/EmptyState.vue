<template>
  <div class="muted-panel empty-state rounded-lg pa-6 pa-sm-8 text-center">
    <v-icon size="40" color="secondary" class="mb-3" aria-hidden="true">{{ icon }}</v-icon>
    <div class="text-subtitle-1 font-weight-bold mb-1">
      <slot name="title">{{ title }}</slot>
    </div>
    <p v-if="text || $scopedSlots.text" class="empty-state__text text-body-2 text--secondary mb-0">
      <slot name="text">{{ text }}</slot>
    </p>
    <div v-if="$scopedSlots.default" class="mt-4">
      <slot />
    </div>
  </div>
</template>

<script>
/** "Nothing here" panel in the admin style (dashed muted panel, icon, title, hint, optional next action). */
export default {
  name: 'EmptyState',
  props: {
    icon: { type: String, default: 'info' },
    title: { type: String, default: '' },
    text: { type: String, default: '' }
  }
}
</script>

<style scoped>
.empty-state__text {
  max-width: 42ch;
  margin-left: auto;
  margin-right: auto;
  line-height: 1.6;
}
</style>
