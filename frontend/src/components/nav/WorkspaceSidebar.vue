<template>
  <v-navigation-drawer
    v-model="drawerOpen"
    :mini-variant="mini"
    mini-variant-width="64"
    :permanent="!isMobile"
    :temporary="isMobile"
    :fixed="isMobile"
    width="280"
    dark
    class="ws-sidebar"
    :class="isMobile ? 'ws-sidebar--mobile' : 'ws-sidebar--desktop'"
    :style="{ background: gradient }"
    @update:mini-variant="onMiniChange"
  >
    <div class="ws-sidebar__inner">
      <div class="ws-sidebar__scroll">
        <!-- Brand: workspace name + who is signed in; chevron collapses to the icon rail -->
        <v-list-item class="ws-sidebar__brand">
          <v-list-item-avatar tile size="32" class="ws-sidebar__mark">
            <v-icon size="20">{{ icon }}</v-icon>
          </v-list-item-avatar>
          <v-list-item-content>
            <v-list-item-title class="ws-sidebar__title">{{ title }}</v-list-item-title>
            <v-list-item-subtitle v-if="subtitle">{{ subtitle }}</v-list-item-subtitle>
          </v-list-item-content>
          <v-list-item-action v-if="!isMobile" class="my-0">
            <v-btn icon small aria-label="Collapse sidebar" @click.stop="setRail(true)">
              <v-icon>chevron_left</v-icon>
            </v-btn>
          </v-list-item-action>
          <v-list-item-action v-else class="my-0">
            <v-btn icon small aria-label="Close menu" @click.stop="drawerOpen = false">
              <v-icon>close</v-icon>
            </v-btn>
          </v-list-item-action>
        </v-list-item>

        <v-divider />

        <slot name="top" :mini="mini" />

        <!-- Pages grouped into sections (item.group); the icon rail shows a thin divider instead of the heading -->
        <v-list dense nav class="ws-sidebar__list">
          <template v-for="(section, si) in sections">
          <div
            v-if="section.title"
            :key="`section-${si}`"
            class="ws-sidebar__section"
            :class="{ 'ws-sidebar__section--mini': mini, 'ws-sidebar__section--first': si === 0 }"
            role="presentation"
          >
            <span v-if="!mini">{{ section.title }}</span>
          </div>
          <v-tooltip
            v-for="item in section.items"
            :key="item.key"
            right
            :disabled="!mini"
            open-delay="150"
          >
            <template #activator="{ on, attrs }">
              <v-list-item
                :to="item.to"
                :exact="item.exact === true"
                link
                class="ws-sidebar__item"
                v-bind="attrs"
                :aria-label="mini ? item.label : undefined"
                v-on="on"
                @click="onNav"
              >
                <v-list-item-icon class="ws-sidebar__icon">
                  <v-icon>{{ item.icon }}</v-icon>
                  <span v-if="mini && item.badge > 0" class="ws-sidebar__dot" aria-hidden="true" />
                </v-list-item-icon>
                <v-list-item-content>
                  <v-list-item-title>{{ item.label }}</v-list-item-title>
                </v-list-item-content>
                <v-list-item-action v-if="item.badge > 0" class="my-0">
                  <span class="ws-sidebar__badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
                </v-list-item-action>
              </v-list-item>
            </template>
            <span>{{ item.label }}{{ item.badge > 0 ? ` (${item.badge})` : '' }}</span>
          </v-tooltip>
          </template>
        </v-list>
      </div>

      <!-- Bottom actions stay pinned while the page list scrolls above -->
      <div class="ws-sidebar__bottom">
        <v-divider />
        <div class="ws-sidebar__footer" :class="{ 'ws-sidebar__footer--mini': mini }">
          <v-tooltip
            v-for="action in footerActions"
            :key="action.key"
            :right="mini"
            :top="!mini"
          >
            <template #activator="{ on, attrs }">
              <v-btn
                icon
                small
                :to="action.to"
                exact
                :aria-label="action.label"
                v-bind="attrs"
                v-on="on"
                @click.stop="onFooter(action)"
              >
                <v-icon>{{ action.icon }}</v-icon>
              </v-btn>
            </template>
            <span>{{ action.label }}</span>
          </v-tooltip>
        </div>
      </div>
    </div>
  </v-navigation-drawer>
</template>

<script>
/** Same look as the Wheel Hub sidebar, in this app's slate palette. */
const GRADIENT = 'linear-gradient(180deg, #0f172a 0%, #1e293b 55%, #334155 100%)'

export default {
  name: 'WorkspaceSidebar',
  props: {
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
    icon: { type: String, default: 'storefront' },
    /**
     * [{ key, label, icon, to, exact?, badge?, group? }] — items sharing a `group` are shown together under
     * that heading, in the order the groups first appear. Items without a group go in an untitled section.
     */
    items: { type: Array, required: true },
    /** [{ key, label, icon, to? }] — actions without `to` are emitted as `action` with their key. */
    footerActions: { type: Array, default: () => [] },
    /** localStorage key that remembers the collapsed rail per workspace. */
    storageKey: { type: String, default: 'ps_sidebar_rail' }
  },
  data() {
    return {
      mobileOpen: false,
      rail: this.readRail(),
      gradient: GRADIENT
    }
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.smAndDown
    },
    mini() {
      return this.rail && !this.isMobile
    },
    /** [{ title, items }] in the order each group first appears; empty groups never show. */
    sections() {
      const out = []
      const byTitle = {}
      for (const item of this.items || []) {
        const title = item.group || ''
        if (!byTitle[title]) {
          byTitle[title] = { title, items: [] }
          out.push(byTitle[title])
        }
        byTitle[title].items.push(item)
      }
      return out
    },
    drawerOpen: {
      get() {
        return this.isMobile ? this.mobileOpen : true
      },
      set(v) {
        if (this.isMobile) this.mobileOpen = v
      }
    }
  },
  watch: {
    isMobile() {
      this.mobileOpen = false
      this.publishWidth()
    },
    mini() {
      this.publishWidth()
    }
  },
  mounted() {
    this.publishWidth()
  },
  beforeDestroy() {
    const root = document.documentElement
    root.classList.remove('has-ws-sidebar')
    root.style.removeProperty('--ws-sidebar-width')
  },
  methods: {
    /**
     * On desktop the sidebar is pinned to the left edge (position: fixed), so it is out of the page flow.
     * Tell the page how wide it is so the content and footer start beside it instead of under it.
     */
    publishWidth() {
      const root = document.documentElement
      const width = this.isMobile ? 0 : this.mini ? 64 : 280
      root.classList.toggle('has-ws-sidebar', width > 0)
      root.style.setProperty('--ws-sidebar-width', `${width}px`)
    },
    open() {
      this.mobileOpen = true
    },
    readRail() {
      try {
        return window.localStorage.getItem(this.storageKey) === '1'
      } catch {
        return false
      }
    },
    setRail(v) {
      this.rail = v
      try {
        window.localStorage.setItem(this.storageKey, v ? '1' : '0')
      } catch {
        /* storage unavailable: keep in memory only */
      }
    },
    /** Clicking the rail expands it, as in Wheel Hub. */
    onMiniChange(v) {
      if (!this.isMobile && v === false) this.setRail(false)
    },
    onNav() {
      if (this.isMobile) this.mobileOpen = false
    },
    onFooter(action) {
      if (this.isMobile) this.mobileOpen = false
      if (!action.to) this.$emit('action', action.key)
    }
  }
}
</script>

<style scoped>
/*
 * Desktop: pinned to the left edge from under the 72px app bar to the bottom of the window, so it always
 * fills the screen height and never scrolls away with the page. The page is shifted right by
 * --ws-sidebar-width (see publishWidth and App.vue).
 */
.ws-sidebar--desktop {
  position: fixed !important;
  top: 72px !important;
  left: 0 !important;
  bottom: 0 !important;
  height: auto !important;
  max-height: none !important;
  z-index: 4;
  box-shadow: 2px 0 5px rgba(0, 0, 0, 0.1);
}

/* Phone: slides over the page below the app bar. */
.ws-sidebar--mobile {
  top: 72px !important;
  height: calc(100% - 72px) !important;
}

.ws-sidebar >>> .v-navigation-drawer__content {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.ws-sidebar__inner {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}

.ws-sidebar__scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
}

.ws-sidebar__section {
  padding: 14px 12px 4px;
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.5);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ws-sidebar__section--first {
  padding-top: 4px;
}

/* Icon rail: no room for words, so a short rule marks where a section starts. */
.ws-sidebar__section--mini {
  padding: 0;
  margin: 8px 14px;
  height: 1px;
  background: rgba(255, 255, 255, 0.15);
}

.ws-sidebar__section--mini.ws-sidebar__section--first {
  display: none;
}

.ws-sidebar__brand {
  padding-top: 10px;
  padding-bottom: 10px;
}

.ws-sidebar__mark {
  background: rgba(255, 255, 255, 0.14);
  border-radius: 8px !important;
}

.ws-sidebar__title {
  font-weight: 700;
}

.ws-sidebar >>> .v-list-item__title,
.ws-sidebar >>> .v-list-item__subtitle {
  color: rgba(255, 255, 255, 0.95) !important;
  white-space: normal;
  overflow-wrap: anywhere;
}

.ws-sidebar >>> .v-list-item__subtitle {
  color: rgba(255, 255, 255, 0.7) !important;
}

.ws-sidebar >>> .v-icon {
  color: rgba(255, 255, 255, 0.9) !important;
}

.ws-sidebar >>> .v-divider {
  border-color: rgba(255, 255, 255, 0.2) !important;
}

.ws-sidebar__item.v-list-item--active::before {
  opacity: 0 !important;
}

.ws-sidebar__item.v-list-item--active {
  background: rgba(255, 255, 255, 0.14) !important;
}

.ws-sidebar__icon {
  position: relative;
}

.ws-sidebar__badge {
  min-width: 1.375rem;
  height: 1.375rem;
  padding: 0 0.35rem;
  border-radius: 0.6875rem;
  background: #ea580c;
  color: #fff;
  font-size: 0.6875rem;
  font-weight: 700;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.ws-sidebar__dot {
  position: absolute;
  top: 2px;
  right: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ea580c;
  box-shadow: 0 0 0 2px #0f172a;
}

.ws-sidebar__bottom {
  flex-shrink: 0;
  background: rgba(0, 0, 0, 0.15);
  backdrop-filter: blur(8px);
}

.ws-sidebar__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px;
}

.ws-sidebar__footer--mini {
  flex-direction: column;
}
</style>
