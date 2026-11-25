<template>
  <div class="client-shell">
    <workspace-sidebar
      ref="sidebar"
      :title="sidebarTitle"
      :subtitle="email"
      icon="person"
      :items="sidebarItems"
      :footer-actions="sidebarFooter"
      storage-key="ps_client_sidebar_rail"
      @action="onSidebarAction"
    />
    <div class="client-shell__main">
      <slot />
    </div>
  </div>
</template>

<script>
import WorkspaceSidebar from '@/components/nav/WorkspaceSidebar.vue'
import { getClientCheckoutPrefill, getSessionUser, logout, subscribeToAuth } from '@/services/auth'
import { clientSidebarFooter, clientSidebarItems, clientWorkspaceTitle } from './clientNav'

/**
 * Signed-in customer workspace: the same sidebar as the provider admin and platform console, with the page
 * beside it. Pages render their own PageHero, which shows the phone "Menu" button through `workspaceMenu`.
 */
export default {
  name: 'ClientShell',
  components: { WorkspaceSidebar },
  provide() {
    return {
      workspaceMenu: {
        open: () => this.$refs.sidebar && this.$refs.sidebar.open()
      }
    }
  },
  data() {
    return {
      user: getSessionUser(),
      sidebarItems: clientSidebarItems(),
      sidebarFooter: clientSidebarFooter()
    }
  },
  computed: {
    email() {
      return (this.user && this.user.email) || ''
    },
    sidebarTitle() {
      void this.user
      return clientWorkspaceTitle(getClientCheckoutPrefill().displayName)
    }
  },
  created() {
    this.unsubAuth = subscribeToAuth((u) => {
      this.user = u
    })
  },
  beforeDestroy() {
    if (this.unsubAuth) this.unsubAuth()
  },
  methods: {
    async onSidebarAction(key) {
      if (key !== 'logout') return
      // Leave the signed-in pages first, so none of them reacts to the cleared session on the way out.
      await this.$router.replace({ name: 'client-login' }).catch(() => {})
      await logout()
    }
  }
}
</script>

<style scoped>
.client-shell {
  display: flex;
  align-items: stretch;
  min-height: 100%;
}

.client-shell__main {
  flex: 1 1 auto;
  min-width: 0;
}
</style>
