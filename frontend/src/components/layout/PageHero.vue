<template>
  <section
    class="admin-hero page-hero"
    :class="{ 'page-hero--success': tone === 'success', 'page-hero--flush': flush }"
  >
    <v-container class="admin-hero__container px-3 px-sm-4" :class="containerClass">
      <div class="admin-hero__inner d-flex flex-column flex-md-row align-start align-md-center">
        <div v-if="$scopedSlots.media" class="page-hero__media mb-4 mb-md-0 mr-md-6">
          <slot name="media" />
        </div>
        <div class="page-hero__text min-width-0">
          <!-- Inside a workspace (client area) the sidebar is a slide-in drawer on phones; this opens it. -->
          <v-btn
            v-if="showMenu"
            small
            depressed
            color="primary"
            class="text-none font-weight-bold mb-3"
            @click="workspaceMenu.open()"
          >
            <v-icon left small>menu</v-icon>
            Menu
          </v-btn>
          <div v-if="kicker" class="admin-kicker mb-2">
            <v-icon v-if="kickerIcon" small color="secondary" class="mr-1" aria-hidden="true">{{ kickerIcon }}</v-icon>
            {{ kicker }}
          </div>
          <h1 class="admin-title">
            <slot name="title">{{ title }}</slot>
          </h1>
          <p v-if="lead || $scopedSlots.lead" class="admin-lead mb-0">
            <slot name="lead">{{ lead }}</slot>
          </p>
          <slot />
        </div>
        <div v-if="$scopedSlots.actions" class="page-hero__actions mt-4 mt-md-0 ml-md-auto">
          <slot name="actions" />
        </div>
      </div>
    </v-container>
  </section>
</template>

<script>
/**
 * Page header band shared by the client area, storefront, checkout, booking and sign in pages. Same markup and
 * classes as the provider admin and platform console headers (styles/workspace.css). Put `admin-body` on the
 * container that follows to let its first card overlap the band, as the admin pages do.
 */
export default {
  name: 'PageHero',
  inject: {
    workspaceMenu: { default: null }
  },
  props: {
    kicker: { type: String, default: '' },
    kickerIcon: { type: String, default: '' },
    title: { type: String, default: '' },
    lead: { type: String, default: '' },
    /** `success` tints the band green (order placed). */
    tone: { type: String, default: 'default' },
    /** The page body does not overlap the band (no `admin-body`): drop the room kept for that overlap. */
    flush: { type: Boolean, default: false },
    /** Extra classes for the inner container, e.g. a max width that matches the page body. */
    containerClass: { type: [String, Array, Object], default: '' }
  },
  computed: {
    showMenu() {
      return Boolean(this.workspaceMenu) && this.$vuetify.breakpoint.smAndDown
    }
  }
}
</script>

<style scoped>
.page-hero__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.page-hero__media {
  flex-shrink: 0;
}

.page-hero--flush .admin-hero__container {
  padding-bottom: 24px;
}

@media (min-width: 960px) {
  .page-hero--flush .admin-hero__container {
    padding-bottom: 32px;
  }
}

.page-hero--success {
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 45%, #f0fdf4 100%);
  border-bottom-color: rgba(21, 128, 61, 0.12);
}

.page-hero--success .admin-kicker {
  color: rgba(22, 101, 52, 0.85);
}

.page-hero--success .admin-title {
  color: #14532d;
}

.page-hero--success .admin-lead {
  color: rgba(20, 83, 45, 0.85);
}
</style>
